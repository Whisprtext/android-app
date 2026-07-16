package com.whisprtext.app.data.repository

import android.content.Context
import com.whisprtext.app.data.local.AppDatabase
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.local.entity.PendingReceiptEntity
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.remote.WebSocketEvent
import com.whisprtext.app.data.remote.WebSocketManager
import com.whisprtext.app.data.remote.model.ConversationSummaryDto
import com.whisprtext.app.data.remote.model.MessageDto
import com.whisprtext.app.util.NetworkMonitor
import com.whisprtext.app.util.NotificationHelper
import com.whisprtext.app.util.MediaCrypto
import android.net.Uri
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChatRepository @JvmOverloads constructor(
    private val database: AppDatabase,
    private val apiClient: ApiClient,
    private val webSocketManager: WebSocketManager,
    private val networkMonitor: NetworkMonitor,
    private val preferencesManager: PreferencesManager,
    private val context: Context? = null,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) {
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val pendingReceiptDao = database.pendingReceiptDao()
    private val scope = CoroutineScope(ioDispatcher)
    private var cachedUserId: String? = null

    /**
     * In-memory map for the edge case where a receipt update (from the server) arrives
     * *before* the corresponding message has been written to Room. The receipt is held
     * here and applied when the message is eventually inserted by upsertMessage().
     */
    private val pendingReceipts = ConcurrentHashMap<String, String>()
    private val notificationHelper = context?.let { NotificationHelper(it) }

    /** Maximum number of HTTP delivery attempts before a pending receipt is abandoned. */
    private val MAX_RECEIPT_ATTEMPTS = 10

    var activeConversationId: String? = null
    var isAppInForeground = false

    init {
        scope.launch {
            try {
                preferencesManager?.userId?.collect { id ->
                    cachedUserId = id
                }
            } catch (e: Exception) {
                // Ignore background coroutine errors in test mocks
            }
        }

        // Collect real-time events from WebSocketManager
        scope.launch {
            try {
                webSocketManager?.events?.collect { event ->
                    val currentUserId = cachedUserId ?: preferencesManager?.userId?.first() ?: ""
                    when (event) {
                        is WebSocketEvent.NewMessage -> {
                            val isCurrentChat = isAppInForeground &&
                                    activeConversationId == event.message.conversationId

                            if (event.message.senderId != currentUserId) {
                                // Reliably report delivery/read status via HTTP, not just WS.
                                // WS call is still fired for real-time feedback but is not required.
                                val receiptStatus = if (isCurrentChat) "read" else "delivered"
                                sendReceiptReliably(
                                    messageId = event.message.id,
                                    conversationId = event.message.conversationId,
                                    status = receiptStatus,
                                )
                                // Best-effort real-time WS notification (non-blocking).
                                if (isCurrentChat) {
                                    webSocketManager?.markMessageRead(event.message.id)
                                } else {
                                    webSocketManager?.markMessageDelivered(event.message.id)
                                }
                            }

                            val entityStatus = when {
                                event.message.senderId == currentUserId -> "sent"
                                isCurrentChat -> "read"
                                else -> "delivered"
                            }

                            upsertMessage(event.message.toEntity(entityStatus))

                            val conv = conversationDao.getById(event.message.conversationId)
                            if (conv != null) {
                                val updatedConv = conv.copy(
                                    lastMessageText = event.message.encryptedContent,
                                    lastMessageTime = event.message.createdAt.toEpochMillis()
                                )
                                conversationDao.insert(updatedConv)
                            } else {
                                scope.launch {
                                    syncConversations()
                                }
                            }

                            if (event.message.senderId != currentUserId && !isCurrentChat) {
                                conversationDao.incrementUnreadCount(event.message.conversationId)
                                val senderName = conv?.title ?: conv?.username ?: "New Message"
                                notificationHelper?.showMessageNotification(
                                    conversationId = event.message.conversationId,
                                    senderName = senderName,
                                    messageText = event.message.encryptedContent
                                )
                            }
                        }
                        is WebSocketEvent.Ack -> {
                            messageDao.deleteById(event.clientMsgId)
                        }
                        is WebSocketEvent.ReceiptUpdate -> {
                            // Only apply receipts that are not from the current user —
                            // receipts from self are managed locally.
                            if (event.receipt.userId != currentUserId) {
                                updateMessageStatus(event.receipt.messageId, event.receipt.status)
                            }
                        }
                        is WebSocketEvent.MessageDeleted -> {
                            messageDao.deleteById(event.messageId)
                        }
                        is WebSocketEvent.Connected -> {
                            // On WebSocket reconnect, flush pending receipts first, then
                            // do a delta sync to catch up on messages missed while offline.
                            syncReceipts()
                            syncDelta()
                            retryFailedMessages()
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Monitor internet connectivity to trigger resync & send retries
        scope.launch {
            try {
                var wasOffline = false
                networkMonitor?.isOnline?.collect { isOnline ->
                    if (isOnline) {
                        if (wasOffline) {
                            // Coming back online: flush pending receipts before delta sync
                            // so the server gets accurate delivery information.
                            syncReceipts()
                            syncDelta()
                            retryFailedMessages()
                        } else {
                            // Resync when initialized while online
                            syncReceipts()
                            syncDelta()
                            retryFailedMessages()
                        }
                        wasOffline = false
                    } else {
                        wasOffline = true
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun getConversations(): Flow<List<ConversationEntity>> = conversationDao.getConversationsFlow()

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> = messageDao.getMessagesForConversation(conversationId)

    suspend fun syncConversations() {
        try {
            val remoteSummaries = apiClient.getConversations()
            if (remoteSummaries.isEmpty()) return

            val entities = remoteSummaries.map { it.toEntity() }
            conversationDao.insertAll(entities)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncMessages(conversationId: String) {
        // No-op to prevent message backfill from cloud history.
    }


    suspend fun sendMessage(conversationId: String, content: String, senderId: String, senderDeviceId: String) {
        val tempId = UUID.randomUUID().toString()
        val localMsg = MessageEntity(
            id = tempId,
            conversationId = conversationId,
            senderId = senderId,
            senderDeviceId = senderDeviceId,
            encryptedContent = content,
            createdAt = System.currentTimeMillis(),
            syncStatus = "pending"
        )
        messageDao.insert(localMsg)

        // Optimistically update conversation last message details
        val conv = conversationDao.getById(conversationId)
        if (conv != null) {
            conversationDao.insert(conv.copy(
                lastMessageText = content,
                lastMessageTime = localMsg.createdAt
            ))
        }

        try {
            val response = apiClient.sendMessage(conversationId, content)
            messageDao.deleteById(tempId)
            upsertMessage(response.toEntity("sent"))

            // Update conversation with exact server timestamp
            val updatedConv = conversationDao.getById(conversationId)
            if (updatedConv != null) {
                conversationDao.insert(updatedConv.copy(
                    lastMessageText = content,
                    lastMessageTime = response.createdAt.toEpochMillis()
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            messageDao.insert(localMsg.copy(syncStatus = "failed"))
        }
    }

    suspend fun syncDelta() {
        try {
            val since = preferencesManager.lastSyncTime.first()
            if (since == null) {
                syncConversations()
                val nowStr = java.time.Instant.now().toString()
                preferencesManager.saveLastSyncTime(nowStr)
                return
            }
            val delta = apiClient.sync(since)
            val currentUserId = preferencesManager.userId.first()

            if (delta.messages.isNotEmpty()) {
                val entities = delta.messages.map {
                    val status = if (it.senderId == currentUserId) "sent" else "delivered"
                    it.toEntity(status)
                }
                upsertMessages(entities)

                // For received messages, reliably report delivery via HTTP so the sender
                // sees double-tick even if the WS was unavailable when the message arrived.
                for (msg in delta.messages) {
                    if (msg.senderId != currentUserId) {
                        sendReceiptReliably(
                            messageId = msg.id,
                            conversationId = msg.conversationId,
                            status = "delivered",
                        )
                    }
                }

                val grouped = delta.messages.groupBy { it.conversationId }
                for ((convId, msgs) in grouped) {
                    val latest = msgs.maxByOrNull { it.createdAt.toEpochMillis() }
                    val conv = conversationDao.getById(convId)
                    if (conv == null) {
                        syncConversations()
                        break
                    } else if (latest != null) {
                        conversationDao.insert(conv.copy(
                            lastMessageText = latest.encryptedContent,
                            lastMessageTime = latest.createdAt.toEpochMillis()
                        ))
                    }
                }
                syncConversations()
            }

            if (delta.receipts.isNotEmpty()) {
                for (receipt in delta.receipts) {
                    if (receipt.userId != currentUserId) {
                        updateMessageStatus(receipt.messageId, receipt.status)
                    }
                }
            }

            if (delta.deletedMessageIds.isNotEmpty()) {
                for (id in delta.deletedMessageIds) {
                    messageDao.deleteById(id)
                }
            }

            preferencesManager.saveLastSyncTime(delta.currentTime)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Flush all pending receipts from the Room queue to the backend.
     *
     * Called on:
     * - WebSocket connect / reconnect
     * - Network becomes available
     * - App comes to foreground (via onStart in MainActivity)
     *
     * Receipts that fail transiently have their attempt counter incremented.
     * Receipts that exceed [MAX_RECEIPT_ATTEMPTS] are abandoned to prevent
     * stale entries from accumulating indefinitely.
     */
    suspend fun syncReceipts() {
        try {
            // First, purge entries that have been retried too many times.
            pendingReceiptDao.deleteExhausted(MAX_RECEIPT_ATTEMPTS)

            val pending = pendingReceiptDao.getAll()
            for (entry in pending) {
                try {
                    val success = apiClient.sendReceipt(entry.messageId, entry.status)
                    if (success) {
                        pendingReceiptDao.deleteById(entry.id)
                        // Also make sure the local message reflects the correct status.
                        updateMessageStatus(entry.messageId, entry.status)
                    } else {
                        pendingReceiptDao.incrementAttempts(entry.id)
                    }
                } catch (e: Exception) {
                    pendingReceiptDao.incrementAttempts(entry.id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Enqueue a receipt to the Room-backed pending queue and attempt to deliver it
     * immediately via HTTP. If the HTTP call fails (offline, server error), the entry
     * stays in Room and will be retried by [syncReceipts] on the next opportunity.
     *
     * If [status] == "read" we remove any previously queued "delivered" receipt for the
     * same message because "read" subsumes "delivered".
     *
     * This function is intentionally **not** a suspend function at the call sites
     * inside WebSocket event handlers — callers launch it in [scope].
     */
    suspend fun sendReceiptReliably(messageId: String, conversationId: String, status: String) {
        try {
            // "read" supersedes "delivered" — remove stale lower-priority entries.
            if (status == "read") {
                pendingReceiptDao.deleteByMessageId(messageId)
            }

            val entry = PendingReceiptEntity(
                id = UUID.randomUUID().toString(),
                messageId = messageId,
                conversationId = conversationId,
                status = status,
            )
            pendingReceiptDao.insert(entry)

            // Attempt immediate HTTP delivery.
            val success = apiClient.sendReceipt(messageId, status)
            if (success) {
                pendingReceiptDao.deleteById(entry.id)
                updateMessageStatus(messageId, status)
            } else {
                pendingReceiptDao.incrementAttempts(entry.id)
            }
        } catch (e: Exception) {
            // Network unavailable or server error — entry stays in DB for retry.
        }
    }

    suspend fun retryFailedMessages() {
        try {
            val failedList = messageDao.getMessagesBySyncStatus("failed")
            for (msg in failedList) {
                try {
                    messageDao.insert(msg.copy(syncStatus = "pending"))

                    val response = if (!msg.attachmentUrl.isNullOrEmpty() || msg.sizeBytes != null) {
                        // It is a media message!
                        val localFile = msg.localFilePath?.let { java.io.File(it) }
                        if (localFile == null || !localFile.exists()) {
                            // Can't retry if local file is gone
                            messageDao.insert(msg.copy(syncStatus = "failed"))
                            continue
                        }
                        val plaintextBytes = localFile.readBytes()
                        val aesKey = MediaCrypto.hexToBytes(msg.encryptedKey ?: "")
                        val encryptedBytes = MediaCrypto.encrypt(plaintextBytes, aesKey)

                        val initRes = apiClient.initMediaUpload(msg.mimeType ?: "image/jpeg", encryptedBytes.size.toLong())
                        val uploadSuccess = apiClient.uploadEncryptedFile(initRes.uploadUrl, encryptedBytes)
                        if (!uploadSuccess) throw Exception("Upload failed")

                        apiClient.completeMediaUpload(initRes.fileId, initRes.fileUrl)

                        val attDto = com.whisprtext.app.data.remote.model.AttachmentDto(
                            id = UUID.randomUUID().toString(),
                            messageId = msg.id,
                            fileUrl = initRes.fileUrl,
                            mimeType = msg.mimeType ?: "image/jpeg",
                            sizeBytes = plaintextBytes.size.toLong(),
                            encryptedKey = msg.encryptedKey
                        )

                        apiClient.sendMessage(
                            conversationId = msg.conversationId,
                            content = msg.encryptedContent,
                            messageId = msg.id,
                            attachment = attDto
                        )
                    } else {
                        // Standard text message retry
                        apiClient.sendMessage(msg.conversationId, msg.encryptedContent)
                    }

                    messageDao.deleteById(msg.id)
                    val localFile = msg.localFilePath
                    upsertMessage(response.toEntity("sent").copy(localFilePath = localFile))

                    val conv = conversationDao.getById(msg.conversationId)
                    if (conv != null) {
                        conversationDao.insert(conv.copy(
                            lastMessageText = msg.encryptedContent,
                            lastMessageTime = response.createdAt.toEpochMillis()
                        ))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    messageDao.insert(msg.copy(syncStatus = "failed"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getStatusPrecedence(status: String): Int {
        return when (status) {
            "pending" -> 0
            "failed" -> 1
            "sent" -> 2
            "delivered" -> 3
            "read" -> 4
            else -> -1
        }
    }

    private suspend fun updateMessageStatus(messageId: String, newStatus: String) {
        val existing = messageDao.getById(messageId)
        if (existing == null) {
            // Message not yet in DB — hold receipt in the in-memory map so it can be
            // applied the next time upsertMessage() is called for this message.
            pendingReceipts[messageId] = newStatus
            return
        }
        if (getStatusPrecedence(newStatus) > getStatusPrecedence(existing.syncStatus)) {
            messageDao.insert(existing.copy(syncStatus = newStatus))
        }
    }

    private suspend fun upsertMessage(message: MessageEntity) {
        var status = message.syncStatus
        val pending = pendingReceipts.remove(message.id)
        if (pending != null && getStatusPrecedence(pending) > getStatusPrecedence(status)) {
            status = pending
        }

        val existing = messageDao.getById(message.id)
        if (existing == null) {
            messageDao.insert(message.copy(syncStatus = status))
        } else if (getStatusPrecedence(status) > getStatusPrecedence(existing.syncStatus)) {
            messageDao.insert(existing.copy(syncStatus = status))
        }
    }

    private suspend fun upsertMessages(messages: List<MessageEntity>) {
        messages.forEach { upsertMessage(it) }
    }

    fun getConversationFlow(id: String): Flow<ConversationEntity?> = conversationDao.getByIdFlow(id)

    suspend fun markConversationAsRead(conversationId: String) {
        val currentUserId = preferencesManager.userId.first() ?: return

        // Mark all received messages in the conversation as read locally.
        messageDao.markConversationMessagesRead(conversationId, currentUserId)
        conversationDao.clearUnreadCount(conversationId)
        notificationHelper?.cancelNotification(conversationId)

        // Best-effort real-time WS signal (fires immediately, even if it fails the
        // HTTP path below will still report accurate receipt status).
        webSocketManager.markConversationRead(conversationId)

        // Queue reliable HTTP receipts for each unread received message.
        // This ensures the sender sees double-tick even if WS is unavailable.
        try {
            val unread = messageDao.getUnreadReceivedMessages(currentUserId)
                .filter { it.conversationId == conversationId }
            for (msg in unread) {
                sendReceiptReliably(msg.id, conversationId, "read")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun createConversation(type: String, members: List<String>): ConversationEntity {
        val response = apiClient.createConversation(type, members)
        val existingLocal = conversationDao.getById(response.id)
        val entity = ConversationEntity(
            id = response.id,
            type = response.type,
            createdAt = response.createdAt.toEpochMillis(),
            unreadCount = existingLocal?.unreadCount ?: 0,
            lastMessageText = existingLocal?.lastMessageText,
            lastMessageTime = existingLocal?.lastMessageTime,
            title = response.displayName ?: response.username ?: existingLocal?.title,
            username = response.username ?: existingLocal?.username,
            phoneNumber = response.phoneNumber ?: existingLocal?.phoneNumber,
            avatarUrl = response.avatarUrl ?: existingLocal?.avatarUrl
        )
        conversationDao.insert(entity)
        return entity
    }

    suspend fun createDirectConversation(targetUserId: String?, username: String?, avatarUrl: String? = null): ConversationEntity {
        val response = apiClient.createDirectConversation(targetUserId, username)
        val existingLocal = conversationDao.getById(response.id)
        val entity = ConversationEntity(
            id = response.id,
            type = response.type,
            createdAt = response.createdAt.toEpochMillis(),
            unreadCount = existingLocal?.unreadCount ?: 0,
            lastMessageText = existingLocal?.lastMessageText,
            lastMessageTime = existingLocal?.lastMessageTime,
            title = response.displayName ?: response.username ?: existingLocal?.title ?: targetUserId ?: username,
            username = response.username ?: existingLocal?.username,
            phoneNumber = response.phoneNumber ?: existingLocal?.phoneNumber,
            avatarUrl = response.avatarUrl ?: avatarUrl ?: existingLocal?.avatarUrl
        )
        conversationDao.insert(entity)
        return entity
    }

    suspend fun getDirectConversationByContact(username: String?, phoneNumber: String?): ConversationEntity? {
        if (username.isNullOrEmpty() && phoneNumber.isNullOrEmpty()) return null
        return conversationDao.getDirectConversationByContact(username, phoneNumber)
    }

    suspend fun searchUserByUsername(username: String) = apiClient.searchUserByUsername(username)
    suspend fun getMe() = apiClient.getMe()
    suspend fun lookupUsersByPhone(phoneNumbers: List<String>) = apiClient.lookupUsersByPhone(phoneNumbers)
    suspend fun updateSettings(
        phoneNumber: String?,
        discoverableByUsername: Boolean,
        discoverableByPhone: Boolean,
        displayName: String? = null,
        phoneNumberVisibility: String? = null
    ) = apiClient.updateSettings(phoneNumber, discoverableByUsername, discoverableByPhone, displayName, phoneNumberVisibility)

    suspend fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        avatarUrl: String
    ) = apiClient.updateProfile(username, displayName, bio, avatarUrl)

    // Mapper utilities
    private fun ConversationSummaryDto.toEntity(): ConversationEntity {
        return ConversationEntity(
            id = id,
            type = type,
            createdAt = createdAt.toEpochMillis(),
            unreadCount = unreadCount,
            lastMessageText = lastMessage?.encryptedContent,
            lastMessageTime = lastMessage?.createdAt?.toEpochMillis(),
            title = displayName ?: username,
            username = username,
            phoneNumber = phoneNumber,
            avatarUrl = avatarUrl
        )
    }

    private fun MessageDto.toEntity(status: String): MessageEntity {
        val firstAtt = attachments?.firstOrNull()
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            senderDeviceId = senderDeviceId,
            encryptedContent = encryptedContent,
            createdAt = createdAt.toEpochMillis(),
            syncStatus = status,
            attachmentUrl = firstAtt?.fileUrl,
            mimeType = firstAtt?.mimeType,
            sizeBytes = firstAtt?.sizeBytes,
            encryptedKey = firstAtt?.encryptedKey
        )
    }

    private fun String.toEpochMillis(): Long {
        return try {
            java.time.Instant.parse(this).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    suspend fun deleteMessage(messageId: String, forEveryone: Boolean) {
        if (forEveryone) {
            val success = apiClient.deleteMessageForEveryone(messageId)
            if (success) {
                messageDao.deleteById(messageId)
            } else {
                throw Exception("Failed to delete message on the server")
            }
        } else {
            messageDao.deleteById(messageId)
        }
    }

    suspend fun deleteConversations(conversationIds: List<String>) {
        conversationIds.forEach { id ->
            try {
                apiClient.deleteConversation(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            conversationDao.deleteById(id)
            messageDao.deleteByConversationId(id)
        }
    }

    suspend fun deleteAllConversations() {
        try {
            apiClient.deleteAllConversations()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        conversationDao.deleteAll()
        messageDao.deleteAll()
    }

    suspend fun registerPushToken(token: String) {
        try {
            val success = apiClient.updatePushToken(token)
            if (success) {
                preferencesManager.savePushToken(token)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun sendMediaMessage(
        conversationId: String,
        uriString: String,
        mimeType: String,
        senderId: String,
        senderDeviceId: String
    ) {
        val context = context ?: throw Exception("Context is null")
        val uri = Uri.parse(uriString)
        val plaintextBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw Exception("Failed to read file bytes")

        if (plaintextBytes.size > 10 * 1024 * 1024) {
            throw Exception("File size exceeds 10MB limit")
        }

        // Generate key and encrypt media
        val aesKey = MediaCrypto.generateAESKey()
        val encryptedBytes = MediaCrypto.encrypt(plaintextBytes, aesKey)

        // Cache file locally
        val cacheDir = File(context.cacheDir, "media_cache").apply { mkdirs() }
        val localFile = File(cacheDir, UUID.randomUUID().toString())
        localFile.writeBytes(plaintextBytes)

        val tempId = UUID.randomUUID().toString()
        val localMsg = MessageEntity(
            id = tempId,
            conversationId = conversationId,
            senderId = senderId,
            senderDeviceId = senderDeviceId,
            encryptedContent = "[Media]",
            createdAt = System.currentTimeMillis(),
            syncStatus = "pending",
            attachmentUrl = "", // Set later on success
            mimeType = mimeType,
            sizeBytes = plaintextBytes.size.toLong(),
            encryptedKey = MediaCrypto.bytesToHex(aesKey),
            localFilePath = localFile.absolutePath
        )
        messageDao.insert(localMsg)

        // Optimistically update conversation
        val conv = conversationDao.getById(conversationId)
        if (conv != null) {
            conversationDao.insert(conv.copy(
                lastMessageText = "[Media]",
                lastMessageTime = localMsg.createdAt
            ))
        }

        try {
            // Step 1: Init media upload
            val initRes = apiClient.initMediaUpload(mimeType, encryptedBytes.size.toLong())

            // Step 2: PUT encrypted bytes to storage
            val uploadSuccess = apiClient.uploadEncryptedFile(initRes.uploadUrl, encryptedBytes)
            if (!uploadSuccess) {
                throw Exception("Failed to upload encrypted file bytes")
            }

            // Step 3: Complete upload
            apiClient.completeMediaUpload(initRes.fileId, initRes.fileUrl)

            // Step 4: Create message with attachment
            val attDto = com.whisprtext.app.data.remote.model.AttachmentDto(
                id = UUID.randomUUID().toString(),
                messageId = tempId,
                fileUrl = initRes.fileUrl,
                mimeType = mimeType,
                sizeBytes = plaintextBytes.size.toLong(),
                encryptedKey = MediaCrypto.bytesToHex(aesKey)
            )

            val response = apiClient.sendMessage(
                conversationId = conversationId,
                content = "[Media]",
                messageId = tempId,
                attachment = attDto
            )

            // Success: delete temp message and insert server confirmed message
            messageDao.deleteById(tempId)

            val finalEntity = response.toEntity("sent").copy(
                localFilePath = localFile.absolutePath
            )
            upsertMessage(finalEntity)

            val updatedConv = conversationDao.getById(conversationId)
            if (updatedConv != null) {
                conversationDao.insert(updatedConv.copy(
                    lastMessageText = "[Media]",
                    lastMessageTime = response.createdAt.toEpochMillis()
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            messageDao.insert(localMsg.copy(syncStatus = "failed"))
        }
    }

    suspend fun downloadAndDecryptMedia(message: MessageEntity): String? {
        val context = context ?: return null
        if (message.attachmentUrl.isNullOrEmpty() || message.encryptedKey.isNullOrEmpty()) {
            return null
        }

        // If local file path is already present and exists, return it
        if (!message.localFilePath.isNullOrEmpty()) {
            val file = File(message.localFilePath)
            if (file.exists()) {
                return message.localFilePath
            }
        }

        try {
            // Step 1: Get download URL
            val downloadRes = apiClient.getMediaDownloadUrl(message.attachmentUrl)

            // Step 2: Download encrypted bytes
            val encryptedBytes = apiClient.downloadEncryptedFile(downloadRes.downloadUrl)

            // Step 3: Decrypt bytes
            val keyBytes = MediaCrypto.hexToBytes(message.encryptedKey)
            val decryptedBytes = MediaCrypto.decrypt(encryptedBytes, keyBytes)

            // Step 4: Cache locally
            val cacheDir = File(context.cacheDir, "media_cache").apply { mkdirs() }
            val localFile = File(cacheDir, UUID.randomUUID().toString())
            localFile.writeBytes(decryptedBytes)

            // Step 5: Update db
            val updatedMsg = message.copy(localFilePath = localFile.absolutePath)
            messageDao.insert(updatedMsg)

            return localFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
