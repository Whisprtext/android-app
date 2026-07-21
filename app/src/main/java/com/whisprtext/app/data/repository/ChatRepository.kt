package com.whisprtext.app.data.repository

import android.content.Context
import android.util.Log
import com.whisprtext.app.data.local.AppDatabase
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.local.entity.OutboxEntity
import com.whisprtext.app.data.local.entity.PendingReceiptEntity
import com.whisprtext.app.data.local.entity.UserProfileEntity
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.remote.WebSocketEvent
import com.whisprtext.app.data.remote.WebSocketManager
import com.whisprtext.app.data.remote.model.ConversationSummaryDto
import com.whisprtext.app.data.remote.model.MessageDto
import com.whisprtext.app.data.remote.model.UserDto
import com.whisprtext.app.util.NetworkMonitor
import com.whisprtext.app.util.NotificationHelper
import com.whisprtext.app.util.MediaCrypto
import com.whisprtext.app.util.ColorGenerator
import com.whisprtext.app.util.AvatarUrlResolver
import android.net.Uri
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChatRepository @JvmOverloads constructor(
    private val database: AppDatabase,
    private val apiClient: ApiClient,
    private val webSocketManager: WebSocketManager,
    private val networkMonitor: NetworkMonitor,
    private val preferencesManager: PreferencesManager,
    private val appContext: Context? = null,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) {
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val outboxDao = database.outboxDao()
    private val pendingReceiptDao = database.pendingReceiptDao()
    private val userProfileDao = database.userProfileDao()
    private val scope = CoroutineScope(ioDispatcher)
    private var cachedUserId: String? = null

    val signalKeyManager: com.whisprtext.app.crypto.SignalKeyManager? by lazy {
        if (appContext != null && preferencesManager != null) {
            com.whisprtext.app.crypto.SignalKeyManager(appContext, apiClient, preferencesManager)
        } else null
    }

    /**
     * In-memory map for the edge case where a receipt update (from the server) arrives
     * *before* the corresponding message has been written to Room. The receipt is held
     * here and applied when the message is eventually inserted by upsertMessage().
     */
    private val pendingReceipts = ConcurrentHashMap<String, String>()
    private val messageCache = ConcurrentHashMap<String, List<MessageEntity>>()
    private val notificationHelper = appContext?.let { NotificationHelper(it) }

    fun getCachedMessages(conversationId: String): List<MessageEntity> = messageCache[conversationId] ?: emptyList()

    suspend fun preloadMessages(conversationId: String) {
        try {
            val list = messageDao.getMessagesListDirect(conversationId)
            if (list.isNotEmpty()) {
                messageCache[conversationId] = list
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Maximum number of HTTP delivery attempts before a pending receipt is abandoned. */
    private val MAX_RECEIPT_ATTEMPTS = 10

    var activeConversationId: String? = null
    var isAppInForeground = false

    init {
        if (appContext == null) {
            com.whisprtext.app.crypto.LocalEncryptor.isEncryptionEnabled = false
        }
        scope.launch {
            try {
                var lastRegisteredFor: String? = null
                preferencesManager?.userId?.collect { id ->
                    cachedUserId = id
                    if (!id.isNullOrEmpty() && appContext != null) {
                        // Heal missing device UUID after app updates (session without device id)
                        var deviceId = preferencesManager.getDeviceId()
                        if (deviceId.isNullOrBlank()) {
                            try {
                                deviceId = apiClient.fetchMeDeviceId()
                                if (!deviceId.isNullOrBlank()) {
                                    preferencesManager.saveDeviceId(deviceId)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SignalE2EE", "device_id_heal_failed ${e.message?.take(60)}")
                            }
                        }
                        if (!deviceId.isNullOrBlank()) {
                            val key = "$id:$deviceId"
                            if (key == lastRegisteredFor) return@collect
                            try {
                                signalKeyManager?.registerDeviceKeysIfNecessary(id, deviceId)
                                lastRegisteredFor = key
                            } catch (e: Exception) {
                                android.util.Log.e(
                                    "SignalE2EE",
                                    "key_register_error reason=${e.javaClass.simpleName}:${e.message?.take(80)}"
                                )
                            }
                        }
                    }
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
                            val localDeviceId = try {
                                preferencesManager.getDeviceId().orEmpty()
                            } catch (_: Exception) {
                                ""
                            }

                            // E2EE envelopes are encrypted for one device only. Ignore any
                            // envelope not addressed to this device (wrong ciphertext → false
                            // "Unable to decrypt" + premature read receipts / triple-tick).
                            val targetDevice = event.message.recipientDeviceId
                            if (!targetDevice.isNullOrBlank() &&
                                localDeviceId.isNotBlank() &&
                                targetDevice != localDeviceId
                            ) {
                                android.util.Log.d(
                                    "SignalE2EE",
                                    "skip_foreign_envelope messageId=${event.message.id} target=$targetDevice local=$localDeviceId"
                                )
                                return@collect
                            }

                            val isCurrentChat = isAppInForeground &&
                                    activeConversationId == event.message.conversationId

                            val entityStatus = when {
                                event.message.senderId == currentUserId -> "sent"
                                isCurrentChat -> "read"
                                else -> "delivered"
                            }

                            val entity = event.message.toEntity(entityStatus)

                            // Never ACK or treat decrypt failure as a normal delivered/read message.
                            if (entity.isDecryptionFailed || entity.decryptionStatus == "failed") {
                                android.util.Log.e(
                                    "SignalE2EE",
                                    "skip_failed_decrypt_no_receipt messageId=${event.message.id} type=${event.message.messageType}"
                                )
                                val existing = messageDao.getById(entity.id)
                                if (existing == null || existing.decryptionStatus != "decrypted") {
                                    // Persist with failed status, never as delivered/read
                                    upsertMessage(entity.copy(syncStatus = "failed"))
                                    refreshConversationListMeta(event.message.conversationId)
                                }
                                return@collect
                            }

                            upsertMessage(entity)

                            // Receipts only after successful local decrypt
                            if (event.message.senderId != currentUserId) {
                                val receiptStatus = if (isCurrentChat) "read" else "delivered"
                                sendReceiptReliably(
                                    messageId = event.message.id,
                                    conversationId = event.message.conversationId,
                                    status = receiptStatus,
                                )
                                if (isCurrentChat) {
                                    webSocketManager?.markMessageRead(event.message.id)
                                } else {
                                    webSocketManager?.markMessageDelivered(event.message.id)
                                }
                            }

                            var conv = conversationDao.getById(event.message.conversationId)
                            if (conv == null) {
                                syncConversations()
                                conv = conversationDao.getById(event.message.conversationId)
                            }

                            refreshConversationListMeta(event.message.conversationId)

                            if (event.message.senderId != currentUserId && !isCurrentChat) {
                                val senderName = conv?.title ?: conv?.username ?: "New Message"
                                val notifText = previewTextForList(entity) ?: "New message"
                                notificationHelper?.showMessageNotification(
                                    conversationId = event.message.conversationId,
                                    senderName = senderName,
                                    messageText = notifText
                                )
                            } else if (isCurrentChat && event.message.senderId != currentUserId) {
                                markConversationAsRead(event.message.conversationId)
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
                        is WebSocketEvent.NewQueuedMessage -> {
                            // Handle queued message delivery via WebSocket
                            val qm = event.message
                            val currentUserId = cachedUserId ?: preferencesManager.userId.first() ?: ""
                            val localDeviceId = preferencesManager.getDeviceId().orEmpty()

                            // Skip envelopes encrypted for another device
                            val target = qm.recipient_device_id
                            if (target.isNotBlank() && localDeviceId.isNotBlank() && target != localDeviceId) {
                                return@collect
                            }

                            // Decrypt and store
                            val status = if (qm.sender_user_id == currentUserId) "sent" else "delivered"

                            // Use client_message_id (actual message ID) not queue UUID (qm.id)
                            // so that delivery receipts match the sender's copy of the message.
                            val msgDto = MessageDto(
                                id = qm.client_message_id,
                                conversationId = qm.conversation_id ?: "",
                                senderId = qm.sender_user_id,
                                senderDeviceId = qm.sender_device_id,
                                recipientDeviceId = qm.recipient_device_id,
                                encryptedContent = qm.ciphertext,
                                messageType = if (qm.ciphertext_type == "prekey") 3 else 2,
                                createdAt = qm.created_at
                            )
                            val entity = msgDto.toEntity(status)
                            upsertMessage(entity)

                            // Send ack — queue UUID for the server API, actual message ID for local update
                            sendQueueAck(qm.id, qm.client_message_id, localDeviceId)

                            // Send delivery receipt using the actual message ID so the sender
                            // can match it to the stored message and show double ticks.
                            if (qm.sender_user_id != currentUserId) {
                                val isCurrentChat = isAppInForeground && activeConversationId == qm.conversation_id
                                val receiptStatus = if (isCurrentChat) "read" else "delivered"
                                if (qm.conversation_id != null) {
                                    sendReceiptReliably(qm.client_message_id, qm.conversation_id, receiptStatus)
                                }
                            }

                            if (qm.conversation_id != null) {
                                refreshConversationListMeta(qm.conversation_id)
                            }
                        }
                        is WebSocketEvent.Connected -> {
                            // On WebSocket reconnect, flush pending receipts first, then
                            // flush outbox, then do a delta sync
                            syncOutbox()
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
                var isFirstSync = true
                networkMonitor?.isOnline?.collect { isOnline ->
                    if (isOnline) {
                        if (isFirstSync) {
                            // Delay initial heavy sync to prioritize startup smoothness
                            delay(1500)
                            isFirstSync = false
                            
                            syncOutbox()
                            syncReceipts()
                            syncDelta()
                            retryFailedMessages()
                        } else if (wasOffline) {
                            // Coming back online: flush outbox and pending receipts before delta sync
                            syncOutbox()
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

    /**
     * Send a message via the offline queue API. The message is first stored
     * in the local outbox, then sent to the server. On success, the outbox
     * entry is updated. On failure, it remains for retry.
     */
    suspend fun sendQueueMessage(
        conversationId: String,
        recipientUserId: String,
        recipientDeviceId: String,
        ciphertextBase64: String,
        ciphertextType: String = "prekey",
        protocolVersion: Int = 1,
        clientMessageId: String = UUID.randomUUID().toString(),
        decryptedContent: String? = null,
        localFilePath: String? = null,
        mimeType: String? = null,
        sizeBytes: Long? = null,
        attachmentUrl: String? = null,
        encryptedKey: String? = null
    ): String {
        val now = System.currentTimeMillis()
        val outboxEntry = OutboxEntity(
            clientMessageId = clientMessageId,
            conversationId = conversationId,
            recipientUserId = recipientUserId,
            recipientDeviceId = recipientDeviceId,
            ciphertextBase64 = ciphertextBase64,
            ciphertextType = ciphertextType,
            protocolVersion = protocolVersion,
            createdAt = now,
            status = "pending",
            decryptedContent = decryptedContent,
            localFilePath = localFilePath,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            attachmentUrl = attachmentUrl,
            encryptedKey = encryptedKey
        )
        outboxDao.insert(outboxEntry)

        // Insert local pending message for immediate UI feedback
        val senderId = cachedUserId ?: preferencesManager.userId.first() ?: ""
        val localDeviceId = preferencesManager.getDeviceId().orEmpty()
        if (decryptedContent != null) {
            val locallyEncrypted = com.whisprtext.app.crypto.LocalEncryptor.encrypt(decryptedContent)
            val localMsg = MessageEntity(
                id = clientMessageId,
                conversationId = conversationId,
                senderId = senderId,
                senderDeviceId = localDeviceId,
                encryptedContent = locallyEncrypted,
                createdAt = now,
                syncStatus = "pending",
                decryptionStatus = "decrypted",
                attachmentUrl = attachmentUrl,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                encryptedKey = encryptedKey,
                localFilePath = localFilePath
            )
            messageDao.insert(localMsg)
        }

        // Attempt immediate send
        tryFlushOutboxEntry(outboxEntry)
        refreshConversationListMeta(conversationId)
        return clientMessageId
    }

    /**
     * Try to send a single outbox entry to the server.
     */
    private suspend fun tryFlushOutboxEntry(entry: OutboxEntity) {
        try {
            outboxDao.updateStatus(entry.clientMessageId, "queued")
            messageDao.updateSyncStatus(entry.clientMessageId, "queued")

            val response = apiClient.sendQueueMessage(
                clientMessageId = entry.clientMessageId,
                recipientUserId = entry.recipientUserId,
                recipientDeviceId = entry.recipientDeviceId,
                ciphertext = entry.ciphertextBase64,
                ciphertextType = entry.ciphertextType,
                protocolVersion = entry.protocolVersion,
                conversationId = entry.conversationId
            )

            if (response != null && (response.status == "queued" || response.status == "delivered")) {
                outboxDao.updateStatus(entry.clientMessageId, response.status)
                messageDao.updateSyncStatus(entry.clientMessageId, response.status)
            } else {
                outboxDao.updateStatus(entry.clientMessageId, "failed")
                messageDao.updateSyncStatus(entry.clientMessageId, "failed")
            }
        } catch (e: Exception) {
            android.util.Log.e("Outbox", "flush failed: ${e.message?.take(60)}")
            outboxDao.updateStatus(entry.clientMessageId, "failed")
            messageDao.updateSyncStatus(entry.clientMessageId, "failed")
        }
    }

    /**
     * Flush all pending/queued outbox entries to the server.
     * Called on reconnect and periodically.
     */
    suspend fun syncOutbox() {
        try {
            val pending = outboxDao.getPending()
            for (entry in pending) {
                tryFlushOutboxEntry(entry)
            }

            val failed = outboxDao.getFailed()
            for (entry in failed) {
                // Retry failed messages with exponential backoff
                if (entry.attemptCount > 0) {
                    val backoff = (1L shl entry.attemptCount.coerceAtMost(6)) * 1000L
                    if (entry.lastAttemptAt != null && System.currentTimeMillis() - entry.lastAttemptAt < backoff) {
                        continue
                    }
                }
                tryFlushOutboxEntry(entry)
            }

            // Clean up old sent entries (>24h)
            val threshold = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            outboxDao.deleteOldSent(threshold)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Send acknowledgement for a queued message back to the server.
     */
    suspend fun sendQueueAck(queueMessageId: String, actualMessageId: String, localDeviceId: String) {
        try {
            apiClient.acknowledgeQueueMessage(queueMessageId, actualMessageId, localDeviceId)
            // Update local message status using the actual message ID (not queue UUID)
            // so the update matches the message stored by upsertMessage().
            messageDao.updateSyncStatus(actualMessageId, "delivered")
        } catch (e: Exception) {
            android.util.Log.e("QueueAck", "ack failed: ${e.message?.take(60)}")
        }
    }

    fun getConversations(): Flow<List<ConversationEntity>> = conversationDao.getConversationsFlow()

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversation(conversationId)
            .onEach { list -> messageCache[conversationId] = list }
            .onStart {
                val cached = getCachedMessages(conversationId)
                if (cached.isNotEmpty()) {
                    emit(cached)
                }
            }

    suspend fun syncConversations() {
        try {
            val remoteSummaries = apiClient.getConversations()
            if (remoteSummaries.isEmpty()) return

            val currentUserId = cachedUserId ?: preferencesManager.userId.first().orEmpty()
            val entities = remoteSummaries.map { summary ->
                val base = summary.toEntity()
                val existingLocal = conversationDao.getById(summary.id)
                // Prefer richer locally cached avatar if server returns empty.
                val cachedProfile = summary.username?.let { userProfileDao.getByUsername(it) }
                // Server last_message is ciphertext — always prefer local decrypted preview.
                val localLatest = messageDao.getLatestForConversation(summary.id)
                val previewText = resolveConversationPreview(
                    localLatest = localLatest,
                    existingPreview = existingLocal?.lastMessageText,
                    remotePlaceholder = base.lastMessageText
                )
                val previewTime = localLatest?.createdAt
                    ?: base.lastMessageTime
                    ?: existingLocal?.lastMessageTime
                // Unread badge: prefer count of local unread messages; fall back to server then local cache
                val localUnread = if (currentUserId.isNotBlank()) {
                    messageDao.countUnreadInConversation(summary.id, currentUserId)
                } else {
                    0
                }
                val unread = when {
                    localLatest != null || localUnread > 0 -> localUnread
                    existingLocal != null -> existingLocal.unreadCount
                    else -> summary.unreadCount
                }
                base.copy(
                    avatarUrl = summary.avatarUrl
                        ?: existingLocal?.avatarUrl
                        ?: cachedProfile?.avatarUrl,
                    gradientStartColor = existingLocal?.gradientStartColor ?: base.gradientStartColor,
                    gradientEndColor = existingLocal?.gradientEndColor ?: base.gradientEndColor,
                    unreadCount = unread.coerceAtLeast(0),
                    lastMessageText = previewText,
                    lastMessageTime = previewTime
                )
            }
            conversationDao.insertAll(entities)
            seedProfilesFromConversations(remoteSummaries)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Conversation list must show device-local plaintext, never transport ciphertext.
     */
    private fun resolveConversationPreview(
        localLatest: MessageEntity?,
        existingPreview: String?,
        remotePlaceholder: String?
    ): String? {
        val fromLocal = localLatest?.let { previewTextForList(it) }
        if (fromLocal != null) return fromLocal

        val fromExisting = existingPreview?.takeIf { it.isNotBlank() && !isPreviewPlaceholder(it) }
        if (fromExisting != null) return fromExisting

        // Do not surface server ciphertext placeholders
        if (remotePlaceholder != null && !isPreviewPlaceholder(remotePlaceholder) &&
            !com.whisprtext.app.crypto.SignalKeyManager.isLikelyCiphertext(remotePlaceholder)
        ) {
            return remotePlaceholder
        }
        return null
    }

    private fun isPreviewPlaceholder(text: String): Boolean {
        return text == "🔒 Encrypted message" ||
            text == "Encrypted message" ||
            text == com.whisprtext.app.crypto.SignalKeyManager.DISPLAY_DECRYPT_FAILED ||
            text == "[Decryption failed]" ||
            com.whisprtext.app.crypto.SignalKeyManager.isLikelyCiphertext(text)
    }

    /** Plaintext (or safe label) for conversation-list preview from a local message row. */
    private fun previewTextForList(message: MessageEntity): String? {
        if (message.isDecryptionFailed) return null
        val text = message.decryptedContent
        if (text.isBlank() || isPreviewPlaceholder(text)) {
            // Media-only
            if (!message.attachmentUrl.isNullOrEmpty() || message.sizeBytes != null) {
                return when {
                    message.mimeType?.startsWith("image/") == true -> "Photo"
                    message.mimeType?.startsWith("video/") == true -> "Video"
                    else -> "Attachment"
                }
            }
            return null
        }
        if (text == "[Media]") {
            return when {
                message.mimeType?.startsWith("image/") == true -> "Photo"
                message.mimeType?.startsWith("video/") == true -> "Video"
                else -> "Attachment"
            }
        }
        return text
    }

    /**
     * Recompute last-message preview + unread badge from the local message DB.
     * Source of truth for the conversation list (not server ciphertext / stale unread).
     */
    suspend fun refreshConversationListMeta(conversationId: String) {
        val conv = conversationDao.getById(conversationId) ?: return
        val currentUserId = cachedUserId ?: preferencesManager.userId.first().orEmpty()
        val latest = messageDao.getLatestForConversation(conversationId)
        val preview = latest?.let { previewTextForList(it) } ?: conv.lastMessageText?.takeIf {
            !isPreviewPlaceholder(it)
        }
        val time = latest?.createdAt ?: conv.lastMessageTime
        val unread = if (currentUserId.isBlank()) {
            conv.unreadCount
        } else {
            messageDao.countUnreadInConversation(conversationId, currentUserId)
        }
        conversationDao.updateListMeta(
            conversationId = conversationId,
            text = preview,
            time = time,
            unreadCount = unread.coerceAtLeast(0)
        )
    }

    suspend fun syncMessages(conversationId: String) {
        // No-op to prevent message backfill from cloud history.
    }


    suspend fun getOrResolveUserId(username: String): String {
        val existing = userProfileDao.getByUsername(username)
        if (existing != null && !existing.id.startsWith("pending_")) {
            return existing.id
        }
        return try {
            val userDto = apiClient.searchUserByUsername(username)
            val resolvedEntity = UserProfileEntity.fromUserDto(userDto, isSelf = false)
            userProfileDao.upsert(resolvedEntity)
            userDto.id
        } catch (e: Exception) {
            existing?.id ?: "pending_$username"
        }
    }

    suspend fun sendMessage(conversationId: String, content: String, senderId: String, senderDeviceId: String) {
        val resolvedDeviceId = preferencesManager.getDeviceId()
            ?: senderDeviceId.takeIf { it.isNotBlank() && it != "android-device" }
            ?: ""
        val tempId = UUID.randomUUID().toString()
        val locallyEncrypted = com.whisprtext.app.crypto.LocalEncryptor.encrypt(content)
        val localMsg = MessageEntity(
            id = tempId,
            conversationId = conversationId,
            senderId = senderId,
            senderDeviceId = resolvedDeviceId,
            encryptedContent = locallyEncrypted,
            createdAt = System.currentTimeMillis(),
            syncStatus = "pending",
            decryptionStatus = "decrypted"
        )
        messageDao.insert(localMsg)

        refreshConversationListMeta(conversationId)

        try {
            // E2EE required — no plaintext fallback
            val response = sendE2EEMessage(conversationId, tempId, content)

            // Keep sender-local plaintext; never try to decrypt recipient-targeted ciphertext
            messageDao.deleteById(tempId)
            upsertMessage(
                response.toEntityForSender(
                    status = if (response.status == "queued") "queued" else "sent",
                    knownPlaintext = content,
                    localDeviceId = resolvedDeviceId
                )
            )
            refreshConversationListMeta(conversationId)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fall back to queue path via outbox
            try {
                val skm = signalKeyManager
                    ?: throw IllegalStateException("SignalKeyManager unavailable")
                val conv = conversationDao.getById(conversationId) ?: throw IllegalStateException("Conversation not found")
                val recipientUsername = conv.username ?: throw IllegalStateException("Recipient username not found")
                val recipientUserId = getOrResolveUserId(recipientUsername)
                skm.registerDeviceKeysIfNecessary(senderId, resolvedDeviceId)
                val recipientBundles = skm.fetchPreKeyBundles(recipientUserId)
                if (recipientBundles.isNotEmpty()) {
                    val gson = com.google.gson.Gson()
                    val payload = com.whisprtext.app.crypto.SignalKeyManager.DecryptedPayload(
                        text = content,
                        attachments = null
                    )
                    val payloadStr = gson.toJson(payload)
                    val target = recipientBundles.first()
                    val envelope = skm.encryptMessage(recipientUserId, target.deviceId, payloadStr, tempId)

                    sendQueueMessage(
                        conversationId = conversationId,
                        recipientUserId = recipientUserId,
                        recipientDeviceId = target.deviceId,
                        ciphertextBase64 = envelope.ciphertext,
                        ciphertextType = if (envelope.messageType == 3) "prekey" else "whisper",
                        clientMessageId = tempId,
                        decryptedContent = content
                    )
                } else {
                    messageDao.insert(localMsg.copy(syncStatus = "failed"))
                }
            } catch (e2: Exception) {
                e2.printStackTrace()
                messageDao.insert(localMsg.copy(syncStatus = "failed"))
            }
            refreshConversationListMeta(conversationId)
        }
    }

    private suspend fun sendE2EEMessage(
        conversationId: String,
        messageId: String,
        content: String,
        attachment: com.whisprtext.app.data.remote.model.AttachmentDto? = null,
        attachments: List<com.whisprtext.app.data.remote.model.AttachmentDto>? = null
    ): com.whisprtext.app.data.remote.model.MessageDto {
        val skm = signalKeyManager
            ?: throw IllegalStateException("SignalKeyManager unavailable — cannot send without E2EE")
        val conv = conversationDao.getById(conversationId) ?: throw IllegalStateException("Conversation not found")
        val recipientUsername = conv.username ?: throw IllegalStateException("Recipient username not found")
        val recipientUserId = getOrResolveUserId(recipientUsername)
        val senderId = cachedUserId ?: preferencesManager.userId.first() ?: ""
        var senderDeviceId = preferencesManager.getDeviceId()
        if (senderDeviceId.isNullOrBlank()) {
            senderDeviceId = apiClient.fetchMeDeviceId()
            if (!senderDeviceId.isNullOrBlank()) {
                preferencesManager.saveDeviceId(senderDeviceId)
            }
        }
        if (senderDeviceId.isNullOrBlank()) {
            throw IllegalStateException("Local device id missing — re-login required for E2EE")
        }

        // Ensure our keys are registered before sending
        skm.registerDeviceKeysIfNecessary(senderId, senderDeviceId)

        // List only (no OTPK claim). OTPK is claimed inside encrypt when a new session is built.
        val recipientBundles = skm.fetchPreKeyBundles(recipientUserId)
        if (recipientBundles.isEmpty()) {
            throw IllegalStateException("Recipient has no registered Signal devices")
        }

        // Multi-device self-sync: list our other devices (also no OTPK claim here)
        val ownBundles = try {
            skm.fetchPreKeyBundles(senderId).filter { it.deviceId != senderDeviceId }
        } catch (e: Exception) {
            emptyList()
        }

        val allTargets = recipientBundles + ownBundles
        val recipientDeviceIds = recipientBundles.map { it.deviceId }.toSet()

        val gson = com.google.gson.Gson()
        val payloadAttachments = (listOfNotNull(attachment) + (attachments ?: emptyList())).map {
            com.whisprtext.app.crypto.SignalKeyManager.AttachmentPayloadDto(
                objectKey = it.fileUrl,
                attachmentKey = it.encryptedKey ?: "",
                nonce = "",
                digest = "",
                mimeType = it.mimeType,
                sizeBytes = it.sizeBytes
            )
        }
        val payload = com.whisprtext.app.crypto.SignalKeyManager.DecryptedPayload(
            text = content,
            attachments = payloadAttachments.ifEmpty { null }
        )
        val payloadStr = gson.toJson(payload)

        val sanitizedAttachment = attachment?.copy(encryptedKey = null)
        val sanitizedAttachments = attachments?.map { it.copy(encryptedKey = null) }

        var primaryResponse: com.whisprtext.app.data.remote.model.MessageDto? = null
        var sentAny = false
        var primaryUsed = false

        for (target in allTargets) {
            try {
                val targetUserId = if (target.deviceId in recipientDeviceIds) recipientUserId else senderId
                // Unique message id per device envelope (UUID PK + idempotency)
                val envelopeId = if (!primaryUsed && target.deviceId in recipientDeviceIds) {
                    primaryUsed = true
                    messageId
                } else {
                    UUID.randomUUID().toString()
                }
                val envelope = skm.encryptMessage(targetUserId, target.deviceId, payloadStr, envelopeId)
                val response = apiClient.sendMessage(
                    conversationId = conversationId,
                    content = envelope.ciphertext,
                    messageId = envelopeId,
                    recipientDeviceId = target.deviceId,
                    messageType = envelope.messageType,
                    attachment = sanitizedAttachment,
                    attachments = sanitizedAttachments?.ifEmpty { null }
                )
                sentAny = true
                if (envelopeId == messageId) {
                    primaryResponse = response
                } else if (primaryResponse == null) {
                    primaryResponse = response
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "SignalE2EE",
                    "encrypt_send_failed targetDevice=${target.deviceId} reason=${e.javaClass.simpleName}"
                )
            }
        }

        if (!sentAny || primaryResponse == null) {
            throw IllegalStateException("Failed to send E2EE message to any target device")
        }

        return primaryResponse
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

            Log.d("ChatRepository", "syncDelta: processing delta from network. delta=$delta")
            val messages = delta?.messages
            if (messages != null && messages.size > 0) {
                val localDeviceId = preferencesManager.getDeviceId().orEmpty()
                val entities = mutableListOf<MessageEntity>()

                for (remote in messages) {
                    // Skip envelopes encrypted for another device (defense in depth;
                    // server already filters by recipient_device_id when device id is set).
                    val target = remote.recipientDeviceId
                    if (!target.isNullOrBlank() && localDeviceId.isNotBlank() && target != localDeviceId) {
                        continue
                    }

                    val status = if (remote.senderId == currentUserId) "sent" else "delivered"
                    val entity = remote.toEntity(status)
                    if (entity.isDecryptionFailed || entity.decryptionStatus == "failed") {
                        val existing = messageDao.getById(entity.id)
                        if (existing == null || existing.decryptionStatus != "decrypted") {
                            entities.add(entity.copy(syncStatus = "failed"))
                        }
                        continue
                    }
                    entities.add(entity)
                }

                upsertMessages(entities)

                // Receipts only after successful decrypt — prevents triple-tick on garbage
                for (entity in entities) {
                    if (entity.senderId != currentUserId &&
                        entity.decryptionStatus == "decrypted" &&
                        !entity.isDecryptionFailed &&
                        entity.syncStatus != "failed"
                    ) {
                        sendReceiptReliably(
                            messageId = entity.id,
                            conversationId = entity.conversationId,
                            status = "delivered",
                        )
                    }
                }

                val grouped = entities.groupBy { it.conversationId }
                var needConvSync = false
                for ((convId, _) in grouped) {
                    val conv = conversationDao.getById(convId)
                    if (conv == null) {
                        needConvSync = true
                    } else {
                        refreshConversationListMeta(convId)
                    }
                }
                if (needConvSync) {
                    syncConversations()
                    for (convId in grouped.keys) {
                        refreshConversationListMeta(convId)
                    }
                }
            }

            val receipts = delta.receipts
            if (receipts != null && receipts.isNotEmpty()) {
                for (receipt in receipts) {
                    if (receipt.userId != currentUserId) {
                        updateMessageStatus(receipt.messageId, receipt.status)
                    }
                }
            }

            val deletedIds = delta.deletedMessageIds
            if (deletedIds != null && deletedIds.isNotEmpty()) {
                for (id in deletedIds) {
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
        val currentUserId = cachedUserId ?: preferencesManager.userId.first() ?: ""
        try {
            val failedList = messageDao.getMessagesBySyncStatus("failed")
            for (msg in failedList) {
                val plain = msg.decryptedContent
                try {
                    messageDao.insert(msg.copy(syncStatus = "pending"))
                    if (msg.decryptionStatus == "failed" ||
                        plain == com.whisprtext.app.crypto.SignalKeyManager.DISPLAY_DECRYPT_FAILED
                    ) {
                        messageDao.insert(msg.copy(syncStatus = "failed"))
                        continue
                    }

                    val response = if (!msg.attachmentUrl.isNullOrEmpty() || msg.sizeBytes != null) {
                        val localFile = msg.localFilePath?.let { java.io.File(it) }
                        if (localFile == null || !localFile.exists()) {
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

                        sendE2EEMessage(
                            conversationId = msg.conversationId,
                            messageId = msg.id,
                            content = plain,
                            attachment = attDto
                        )
                    } else {
                        sendE2EEMessage(
                            conversationId = msg.conversationId,
                            messageId = msg.id,
                            content = plain
                        )
                    }

                    messageDao.deleteById(msg.id)
                    val localFile = msg.localFilePath
                    val localDeviceId = preferencesManager.getDeviceId() ?: msg.senderDeviceId
                    upsertMessage(
                        response.toEntityForSender(
                            status = if (response.status == "queued") "queued" else "sent",
                            knownPlaintext = plain,
                            localDeviceId = localDeviceId
                        ).copy(localFilePath = localFile)
                    )

                    val updatedConv = conversationDao.getById(msg.conversationId)
                    if (updatedConv != null) {
                        conversationDao.insert(updatedConv.copy(
                            lastMessageText = plain,
                            lastMessageTime = response.createdAt.toEpochMillis()
                        ))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fall back to outbox for retry
                    try {
                        val localDeviceId = preferencesManager.getDeviceId() ?: msg.senderDeviceId
                        val conv = conversationDao.getById(msg.conversationId)
                        if (conv != null && msg.encryptedContent.isNotBlank() &&
                            !com.whisprtext.app.crypto.SignalKeyManager.isLikelyCiphertext(plain)
                        ) {
                            val skm = signalKeyManager ?: continue
                            val recipientUsername = conv.username ?: continue
                            val recipientUserId = getOrResolveUserId(recipientUsername)
                            val bundles = skm.fetchPreKeyBundles(recipientUserId)
                            if (bundles.isNotEmpty()) {
                                skm.registerDeviceKeysIfNecessary(currentUserId, localDeviceId)
                                val gson = com.google.gson.Gson()
                                val payload = com.whisprtext.app.crypto.SignalKeyManager.DecryptedPayload(
                                    text = plain,
                                    attachments = null
                                )
                                val payloadStr = gson.toJson(payload)
                                val target = bundles.first()
                                val envelope = skm.encryptMessage(recipientUserId, target.deviceId, payloadStr, msg.id)
                                sendQueueMessage(
                                    conversationId = msg.conversationId,
                                    recipientUserId = recipientUserId,
                                    recipientDeviceId = target.deviceId,
                                    ciphertextBase64 = envelope.ciphertext,
                                    clientMessageId = msg.id,
                                    decryptedContent = plain
                                )
                            }
                        }
                    } catch (e2: Exception) {
                        messageDao.insert(msg.copy(syncStatus = "failed"))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getStatusPrecedence(status: String): Int {
        return when (status) {
            "pending" -> 0
            "queued" -> 1
            "failed" -> 2
            "sent" -> 3
            "delivered" -> 4
            "read" -> 5
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
        } else {
            // Prefer already-decrypted local content over a failed remote re-map
            val keepLocalPlaintext = existing.decryptionStatus == "decrypted" &&
                message.decryptionStatus == "failed"
            val mergedDecryptStatus = when {
                keepLocalPlaintext -> existing.decryptionStatus
                message.decryptionStatus == "decrypted" -> "decrypted"
                existing.decryptionStatus == "decrypted" -> existing.decryptionStatus
                else -> message.decryptionStatus
            }
            messageDao.insert(existing.copy(
                syncStatus = if (getStatusPrecedence(status) > getStatusPrecedence(existing.syncStatus)) status else existing.syncStatus,
                encryptedContent = when {
                    keepLocalPlaintext -> existing.encryptedContent
                    message.encryptedContent != "[Media]" -> message.encryptedContent
                    else -> existing.encryptedContent
                },
                senderDeviceId = message.senderDeviceId.ifBlank { existing.senderDeviceId },
                attachmentUrl = message.attachmentUrl?.takeIf { it.isNotEmpty() } ?: existing.attachmentUrl,
                mimeType = message.mimeType?.takeIf { it.isNotEmpty() } ?: existing.mimeType,
                sizeBytes = message.sizeBytes ?: existing.sizeBytes,
                encryptedKey = message.encryptedKey?.takeIf { it.isNotEmpty() } ?: existing.encryptedKey,
                decryptionStatus = mergedDecryptStatus,
                localFilePath = existing.localFilePath ?: message.localFilePath
            ))
        }
    }

    private suspend fun upsertMessages(messages: List<MessageEntity>) {
        messages.forEach { upsertMessage(it) }
    }

    fun getConversationFlow(id: String): Flow<ConversationEntity?> = conversationDao.getByIdFlow(id)

    suspend fun markConversationAsRead(conversationId: String) {
        val currentUserId = preferencesManager.userId.first() ?: return

        // Only ACK messages that decrypted successfully — never receipt "Unable to decrypt".
        val unreadIds = try {
            messageDao.getUnreadReceivedMessages(currentUserId)
                .filter { it.conversationId == conversationId }
                .filter { it.decryptionStatus == "decrypted" && !it.isDecryptionFailed }
                .map { it.id }
        } catch (e: Exception) {
            emptyList()
        }

        // Mark all received messages in the conversation as read locally (incl. failed decrypt rows).
        messageDao.markConversationMessagesRead(conversationId, currentUserId)
        conversationDao.clearUnreadCount(conversationId)
        refreshConversationListMeta(conversationId)
        notificationHelper?.cancelNotification(conversationId)

        webSocketManager.markConversationRead(conversationId)

        try {
            for (id in unreadIds) {
                sendReceiptReliably(id, conversationId, "read")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun createConversation(type: String, members: List<String>): ConversationEntity {
        val response = apiClient.createConversation(type, members)
        val existingLocal = conversationDao.getById(response.id)
        val (start, end) = if (existingLocal?.gradientStartColor != null) {
            existingLocal.gradientStartColor to existingLocal.gradientEndColor
        } else {
            ColorGenerator.generateGradient(response.id)
        }
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
            avatarUrl = response.avatarUrl ?: existingLocal?.avatarUrl,
            gradientStartColor = start,
            gradientEndColor = end
        )
        conversationDao.insert(entity)
        return entity
    }

    suspend fun createDirectConversation(targetUserId: String?, username: String?, avatarUrl: String? = null): ConversationEntity {
        val response = apiClient.createDirectConversation(targetUserId, username)
        val existingLocal = conversationDao.getById(response.id)
        val (start, end) = if (existingLocal?.gradientStartColor != null) {
            existingLocal.gradientStartColor to existingLocal.gradientEndColor
        } else {
            ColorGenerator.generateGradient(response.id)
        }
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
            avatarUrl = response.avatarUrl ?: avatarUrl ?: existingLocal?.avatarUrl,
            gradientStartColor = start,
            gradientEndColor = end
        )
        conversationDao.insert(entity)
        return entity
    }

    suspend fun getDirectConversationByContact(username: String?, phoneNumber: String?): ConversationEntity? {
        if (username.isNullOrEmpty() && phoneNumber.isNullOrEmpty()) return null
        return conversationDao.getDirectConversationByContact(username, phoneNumber)
    }

    // ── Profile local cache ──────────────────────────────────────────────────

    fun observeProfileByUsername(username: String): Flow<UserDto?> =
        userProfileDao.observeByUsername(username).map { it?.toUserDto() }

    fun observeSelfProfile(): Flow<UserDto?> =
        userProfileDao.observeSelf().map { it?.toUserDto() }

    suspend fun getCachedProfileByUsername(username: String): UserDto? =
        userProfileDao.getByUsername(username)?.toUserDto()

    suspend fun getCachedProfileById(userId: String): UserDto? =
        userProfileDao.getById(userId)?.toUserDto()

    suspend fun getCachedSelfProfile(): UserDto? {
        userProfileDao.getSelf()?.toUserDto()?.let { return it }
        val snap = preferencesManager.getOwnProfileSnapshot() ?: return null
        return UserDto(
            id = snap.id,
            username = snap.username,
            phoneNumber = snap.phoneNumber,
            discoverableByUsername = snap.discoverableByUsername,
            discoverableByPhone = snap.discoverableByPhone,
            displayName = snap.displayName,
            bio = snap.bio,
            avatarUrl = snap.avatarUrl,
            phoneNumberVisibility = snap.phoneNumberVisibility
        )
    }

    /**
     * Persist a user profile locally and push avatar/name into matching direct conversations
     * so avatars update everywhere in the app immediately.
     */
    suspend fun cacheUserProfile(user: UserDto, isSelf: Boolean = false, previousUsername: String? = null) {
        val previous = if (previousUsername != null) {
            userProfileDao.getByUsername(previousUsername)
        } else {
            userProfileDao.getById(user.id) ?: userProfileDao.getByUsername(user.username)
        }
        val previousAvatar = previous?.avatarUrl
        val matchUsername = previousUsername ?: previous?.username ?: user.username

        // Drop seed rows (pending_*) or username-collision rows so REPLACE on real id works cleanly.
        val byUsername = userProfileDao.getByUsername(user.username)
        if (byUsername != null && byUsername.id != user.id) {
            userProfileDao.deleteById(byUsername.id)
        }
        if (previous != null && previous.id != user.id && previous.username != user.username) {
            userProfileDao.deleteById(previous.id)
        }

        userProfileDao.upsert(UserProfileEntity.fromUserDto(user, isSelf = isSelf))

        // Keep conversation list / chat headers in sync with the latest profile photo & name.
        conversationDao.updateDirectConversationProfile(
            matchUsername = matchUsername,
            username = user.username,
            displayName = user.displayName.ifBlank { user.username },
            avatarUrl = user.avatarUrl.ifBlank { null },
            phoneNumber = user.phoneNumber
        )

        if (isSelf) {
            preferencesManager.saveOwnProfile(
                userId = user.id,
                username = user.username,
                displayName = user.displayName,
                bio = user.bio,
                avatarUrl = user.avatarUrl,
                phoneNumber = user.phoneNumber,
                phoneNumberVisibility = user.phoneNumberVisibility,
                discoverableByUsername = user.discoverableByUsername,
                discoverableByPhone = user.discoverableByPhone
            )
        }

        // Bust image loader cache when the avatar reference changes.
        if (!previousAvatar.isNullOrBlank() && previousAvatar != user.avatarUrl) {
            AvatarUrlResolver.invalidate(previousAvatar)
            appContext?.let { AvatarUrlResolver.evictFromImageLoader(it, previousAvatar) }
        }
        if (user.avatarUrl.isNotBlank()) {
            AvatarUrlResolver.invalidate(user.avatarUrl)
            appContext?.let { AvatarUrlResolver.evictFromImageLoader(it, user.avatarUrl) }
        }
    }

    suspend fun cacheUserProfiles(users: List<UserDto>, isSelf: Boolean = false) {
        users.forEach { cacheUserProfile(it, isSelf = isSelf) }
    }

    /** Network search + local cache write. */
    suspend fun searchUserByUsername(username: String, cacheResult: Boolean = true): UserDto {
        val user = apiClient.searchUserByUsername(username)
        if (cacheResult) {
            cacheUserProfile(user, isSelf = false)
        }
        return user
    }

    /**
     * Refresh a contact profile from the network and update local cache + conversations.
     * Used when opening another user's profile screen.
     */
    suspend fun refreshContactProfile(username: String): UserDto {
        val previous = userProfileDao.getByUsername(username)
        val user = apiClient.searchUserByUsername(username)
        cacheUserProfile(user, isSelf = false, previousUsername = previous?.username ?: username)
        return user
    }

    suspend fun getMe() = apiClient.getMe()

    /** Fetch own profile from network and persist locally. Prefer [getCachedSelfProfile] for UI loads. */
    suspend fun refreshOwnProfileFromNetwork(): UserDto {
        val me = apiClient.getMe()
        cacheUserProfile(me.user, isSelf = true)
        return me.user
    }

    suspend fun lookupUsersByPhone(phoneNumbers: List<String>): List<UserDto> {
        val users = apiClient.lookupUsersByPhone(phoneNumbers)
        cacheUserProfiles(users, isSelf = false)
        return users
    }

    suspend fun updateSettings(
        phoneNumber: String?,
        discoverableByUsername: Boolean,
        discoverableByPhone: Boolean,
        displayName: String? = null,
        phoneNumberVisibility: String? = null
    ): UserDto {
        val updated = apiClient.updateSettings(
            phoneNumber,
            discoverableByUsername,
            discoverableByPhone,
            displayName,
            phoneNumberVisibility
        )
        cacheUserProfile(updated, isSelf = true)
        return updated
    }

    suspend fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        avatarUrl: String = ""
    ): UserDto {
        val previousUsername = preferencesManager.username.first()
        val updated = apiClient.updateProfile(username, displayName, bio, avatarUrl)
        cacheUserProfile(updated, isSelf = true, previousUsername = previousUsername)
        return updated
    }

    suspend fun setAvatar(fileId: String, fileUrl: String, mimeType: String, sizeBytes: Long): UserDto {
        val updated = apiClient.setAvatar(fileId, fileUrl, mimeType, sizeBytes)
        cacheUserProfile(updated, isSelf = true)
        return updated
    }

    suspend fun removeAvatar(): UserDto {
        val updated = apiClient.removeAvatar()
        cacheUserProfile(updated, isSelf = true)
        return updated
    }

    suspend fun initAvatarUpload(mimeType: String, sizeBytes: Long) =
        apiClient.initAvatarUpload(mimeType, sizeBytes)

    // Mapper utilities
    private fun ConversationSummaryDto.toEntity(): ConversationEntity {
        val (start, end) = ColorGenerator.generateGradient(id)
        // lastMessageText is filled in syncConversations from the local decrypted message DB.
        // Server last_message is ciphertext-only and must not be used as UI preview.
        return ConversationEntity(
            id = id,
            type = type,
            createdAt = createdAt.toEpochMillis(),
            unreadCount = unreadCount,
            lastMessageText = null,
            lastMessageTime = lastMessage?.createdAt?.toEpochMillis(),
            title = displayName ?: username,
            username = username,
            phoneNumber = phoneNumber,
            avatarUrl = avatarUrl,
            gradientStartColor = start,
            gradientEndColor = end
        )
    }

    /**
     * When conversation summaries arrive from the server, seed lightweight contact profiles
     * so avatars/names are available offline without opening each profile.
     */
    suspend fun seedProfilesFromConversations(conversations: List<ConversationSummaryDto>) {
        for (conv in conversations) {
            val uname = conv.username ?: continue
            if (uname.isBlank()) continue
            val existing = userProfileDao.getByUsername(uname)
            val newAvatar = conv.avatarUrl.orEmpty()
            if (existing == null) {
                userProfileDao.upsert(
                    UserProfileEntity(
                        // Real server user id is filled on next profile refresh.
                        id = "pending_$uname",
                        username = uname,
                        displayName = conv.displayName ?: uname,
                        bio = "",
                        avatarUrl = newAvatar,
                        phoneNumber = conv.phoneNumber,
                        isSelf = false
                    )
                )
            } else if (newAvatar.isNotBlank() && newAvatar != existing.avatarUrl) {
                userProfileDao.upsert(
                    existing.copy(
                        displayName = conv.displayName?.takeIf { it.isNotBlank() } ?: existing.displayName,
                        avatarUrl = newAvatar,
                        phoneNumber = conv.phoneNumber ?: existing.phoneNumber,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /**
     * Map a remote message for the *sender* who already knows the plaintext.
     * Never attempt Signal decrypt of a recipient-targeted ciphertext on the sending device.
     */
    private fun MessageDto.toEntityForSender(
        status: String,
        knownPlaintext: String,
        localDeviceId: String
    ): MessageEntity {
        val firstAtt = attachments?.firstOrNull()
        val locallyEncrypted = com.whisprtext.app.crypto.LocalEncryptor.encrypt(knownPlaintext)
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            senderDeviceId = senderDeviceId.ifBlank { localDeviceId },
            encryptedContent = locallyEncrypted,
            createdAt = createdAt.toEpochMillis(),
            syncStatus = status,
            attachmentUrl = firstAtt?.fileUrl,
            mimeType = firstAtt?.mimeType,
            sizeBytes = firstAtt?.sizeBytes,
            encryptedKey = firstAtt?.encryptedKey,
            decryptionStatus = "decrypted"
        )
    }

    /**
     * Map a remote message for local display. Decrypts Signal ciphertext once;
     * stores only LocalEncryptor-wrapped plaintext. Never persists transport ciphertext
     * as user-visible content.
     */
    private fun MessageDto.toEntity(status: String): MessageEntity {
        val firstAtt = attachments?.firstOrNull()
        val currentUserId = cachedUserId ?: ""
        val localDeviceId = try {
            kotlinx.coroutines.runBlocking { preferencesManager.getDeviceId() } ?: ""
        } catch (_: Exception) {
            ""
        }

        // If this is our own outbound envelope for another device, skip (or we may already
        // have a local copy). Still try decrypt only when targeted at this device (sync).
        val isOwnOutbound = senderId == currentUserId
        val targetedAtUs = recipientDeviceId.isNullOrBlank() ||
            recipientDeviceId == localDeviceId

        var decryptionStatus = "decrypted"
        var plaintext: String? = null

        if (isOwnOutbound && !targetedAtUs) {
            // Echo of a copy encrypted for someone else — do not decrypt or display ciphertext.
            // Prefer not inserting; caller may still insert — mark as non-display failure-safe.
            decryptionStatus = "failed"
            plaintext = null
        } else if (messageType == org.whispersystems.libsignal.protocol.CiphertextMessage.PREKEY_TYPE ||
            messageType == org.whispersystems.libsignal.protocol.CiphertextMessage.WHISPER_TYPE
        ) {
            try {
                plaintext = kotlinx.coroutines.runBlocking {
                    signalKeyManager?.decryptMessage(
                        senderUserId = senderId,
                        senderDeviceId = senderDeviceId,
                        ciphertextBase64 = encryptedContent,
                        messageType = messageType,
                        messageId = id,
                        recipientDeviceId = recipientDeviceId
                    )
                }
                if (plaintext == null) {
                    decryptionStatus = "failed"
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "SignalE2EE",
                    "toEntity_decrypt_failed messageId=$id type=$messageType reason=${e.javaClass.simpleName}"
                )
                decryptionStatus = "failed"
                plaintext = null
            }
        } else if (messageType == 0) {
            // Legacy unencrypted transport — do not treat as success for E2EE product path.
            // If it looks like ciphertext, refuse to display; otherwise allow migration text.
            if (com.whisprtext.app.crypto.SignalKeyManager.isLikelyCiphertext(encryptedContent)) {
                decryptionStatus = "failed"
                plaintext = null
            } else {
                plaintext = encryptedContent
            }
        } else {
            decryptionStatus = "failed"
            plaintext = null
        }

        var finalText = if (decryptionStatus == "failed") {
            // Store a non-sensitive placeholder under LocalEncryptor — never the ciphertext
            com.whisprtext.app.crypto.SignalKeyManager.DISPLAY_DECRYPT_FAILED
        } else {
            plaintext ?: ""
        }

        var firstAttUrl = firstAtt?.fileUrl
        var firstAttMime = firstAtt?.mimeType
        var firstAttSize = firstAtt?.sizeBytes
        var firstAttKey = firstAtt?.encryptedKey

        if (decryptionStatus == "decrypted" && !plaintext.isNullOrBlank()) {
            try {
                val gson = com.google.gson.Gson()
                val payload = gson.fromJson(
                    plaintext,
                    com.whisprtext.app.crypto.SignalKeyManager.DecryptedPayload::class.java
                )
                if (payload != null) {
                    finalText = payload.text ?: ""
                    val attPayload = payload.attachments?.firstOrNull()
                    if (attPayload != null) {
                        firstAttUrl = attPayload.objectKey
                        firstAttMime = attPayload.mimeType
                        firstAttSize = attPayload.sizeBytes
                        firstAttKey = attPayload.attachmentKey
                    }
                }
            } catch (_: Exception) {
                // Not a JSON payload — treat as raw text
            }
        }

        // Final guard: never store raw ciphertext as local display content
        if (com.whisprtext.app.crypto.SignalKeyManager.isLikelyCiphertext(finalText)) {
            finalText = com.whisprtext.app.crypto.SignalKeyManager.DISPLAY_DECRYPT_FAILED
            decryptionStatus = "failed"
        }

        val locallyEncrypted = com.whisprtext.app.crypto.LocalEncryptor.encrypt(finalText)

        return MessageEntity(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            senderDeviceId = senderDeviceId,
            encryptedContent = locallyEncrypted,
            createdAt = createdAt.toEpochMillis(),
            syncStatus = status,
            attachmentUrl = firstAttUrl,
            mimeType = firstAttMime,
            sizeBytes = firstAttSize,
            encryptedKey = firstAttKey,
            decryptionStatus = decryptionStatus
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
        senderDeviceId: String,
        content: String = "[Media]",
        extraUris: List<Pair<String, String>> = emptyList()
    ) {
        val ctx = appContext ?: throw Exception("Context is null")

        // Upload primary media
        val primaryUri = Uri.parse(uriString)
        val primaryStream = ctx.contentResolver.openInputStream(primaryUri) ?: throw Exception("Failed to open input stream")
        val plaintextBytes = try { primaryStream.readBytes() } finally { primaryStream.close() }

        if (plaintextBytes.size > 10 * 1024 * 1024) {
            throw Exception("File size exceeds 10MB limit")
        }

        val aesKey = MediaCrypto.generateAESKey()
        val encryptedBytes = MediaCrypto.encrypt(plaintextBytes, aesKey)

        val cacheDir = File(ctx.cacheDir, "media_cache").apply { mkdirs() }
        val localFile = File(cacheDir, UUID.randomUUID().toString())
        localFile.writeBytes(plaintextBytes)

        val tempId = UUID.randomUUID().toString()
        val displayContent = if (content.isNotBlank() && content != "[Media]") content else "[Media]"

        val locallyEncrypted = com.whisprtext.app.crypto.LocalEncryptor.encrypt(displayContent)
        val localMsg = MessageEntity(
            id = tempId,
            conversationId = conversationId,
            senderId = senderId,
            senderDeviceId = senderDeviceId,
            encryptedContent = locallyEncrypted,
            createdAt = System.currentTimeMillis(),
            syncStatus = "pending",
            attachmentUrl = "",
            mimeType = mimeType,
            sizeBytes = plaintextBytes.size.toLong(),
            encryptedKey = MediaCrypto.bytesToHex(aesKey),
            localFilePath = localFile.absolutePath
        )
        messageDao.insert(localMsg)
        refreshConversationListMeta(conversationId)

        val localDeviceId = preferencesManager.getDeviceId() ?: senderDeviceId
        var initRes: com.whisprtext.app.data.remote.model.MediaUploadInitResponse? = null
        val attDtos = mutableListOf<com.whisprtext.app.data.remote.model.AttachmentDto>()

        try {
            // Step 1-3: Upload primary media
            initRes = apiClient.initMediaUpload(mimeType, encryptedBytes.size.toLong())
            val uploadSuccess = apiClient.uploadEncryptedFile(initRes.uploadUrl, encryptedBytes)
            if (!uploadSuccess) throw Exception("Failed to upload encrypted file bytes")
            apiClient.completeMediaUpload(initRes.fileId, initRes.fileUrl)

            attDtos.add(
                com.whisprtext.app.data.remote.model.AttachmentDto(
                    id = UUID.randomUUID().toString(),
                    messageId = tempId,
                    fileUrl = initRes.fileUrl,
                    mimeType = mimeType,
                    sizeBytes = plaintextBytes.size.toLong(),
                    encryptedKey = MediaCrypto.bytesToHex(aesKey)
                )
            )

            // Upload extra media files
            for ((extraUriStr, extraMime) in extraUris) {
                val extraUri = Uri.parse(extraUriStr)
                val extraStream = ctx.contentResolver.openInputStream(extraUri) ?: continue
                val extraPlaintext = try { extraStream.readBytes() } finally { extraStream.close() }
                if (extraPlaintext.size > 10 * 1024 * 1024) continue

                val extraKey = MediaCrypto.generateAESKey()
                val extraEncrypted = MediaCrypto.encrypt(extraPlaintext, extraKey)

                val extraInit = apiClient.initMediaUpload(extraMime, extraEncrypted.size.toLong())
                val extraUploadOk = apiClient.uploadEncryptedFile(extraInit.uploadUrl, extraEncrypted)
                if (!extraUploadOk) continue
                apiClient.completeMediaUpload(extraInit.fileId, extraInit.fileUrl)

                attDtos.add(
                    com.whisprtext.app.data.remote.model.AttachmentDto(
                        id = UUID.randomUUID().toString(),
                        messageId = tempId,
                        fileUrl = extraInit.fileUrl,
                        mimeType = extraMime,
                        sizeBytes = extraPlaintext.size.toLong(),
                        encryptedKey = MediaCrypto.bytesToHex(extraKey)
                    )
                )
            }

            // E2EE only — AES keys are packed into the Signal payload inside sendE2EEMessage,
            // then stripped from the attachment metadata sent to the server.
            val response = sendE2EEMessage(
                conversationId = conversationId,
                messageId = tempId,
                content = displayContent,
                attachment = attDtos.first(),
                attachments = attDtos.drop(1).ifEmpty { null }
            )

            messageDao.deleteById(tempId)
            val finalEntity = response.toEntityForSender(
                status = if (response.status == "queued") "queued" else "sent",
                knownPlaintext = displayContent,
                localDeviceId = localDeviceId
            ).copy(
                localFilePath = localFile.absolutePath,
                attachmentUrl = attDtos.first().fileUrl,
                mimeType = mimeType,
                sizeBytes = plaintextBytes.size.toLong(),
                encryptedKey = MediaCrypto.bytesToHex(aesKey)
            )
            upsertMessage(finalEntity)
            refreshConversationListMeta(conversationId)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fall back to queue via outbox
            try {
                val skm = signalKeyManager ?: throw Exception("SignalKeyManager unavailable")
                val conv = conversationDao.getById(conversationId) ?: throw Exception("Conversation not found")
                val recipientUsername = conv.username ?: throw Exception("No recipient")
                val recipientUserId = getOrResolveUserId(recipientUsername)
                skm.registerDeviceKeysIfNecessary(senderId, localDeviceId)
                val bundles = skm.fetchPreKeyBundles(recipientUserId)
                if (bundles.isNotEmpty() && initRes != null && attDtos.isNotEmpty()) {
                    val gson = com.google.gson.Gson()
                    val attPayload = com.whisprtext.app.crypto.SignalKeyManager.AttachmentPayloadDto(
                        objectKey = initRes.fileUrl,
                        attachmentKey = MediaCrypto.bytesToHex(aesKey),
                        nonce = "", digest = "",
                        mimeType = mimeType, sizeBytes = plaintextBytes.size.toLong()
                    )
                    val payload = com.whisprtext.app.crypto.SignalKeyManager.DecryptedPayload(
                        text = displayContent,
                        attachments = listOf(attPayload)
                    )
                    val payloadStr = gson.toJson(payload)
                    val target = bundles.first()
                    val envelope = skm.encryptMessage(recipientUserId, target.deviceId, payloadStr, tempId)
                    sendQueueMessage(
                        conversationId = conversationId,
                        recipientUserId = recipientUserId,
                        recipientDeviceId = target.deviceId,
                        ciphertextBase64 = envelope.ciphertext,
                        clientMessageId = tempId,
                        decryptedContent = displayContent,
                        localFilePath = localFile.absolutePath,
                        mimeType = mimeType,
                        sizeBytes = plaintextBytes.size.toLong(),
                        attachmentUrl = attDtos.first().fileUrl,
                        encryptedKey = MediaCrypto.bytesToHex(aesKey)
                    )
                }
            } catch (e2: Exception) {
                e2.printStackTrace()
                messageDao.insert(localMsg.copy(syncStatus = "failed"))
            }
            refreshConversationListMeta(conversationId)
        }
    }

    suspend fun downloadAndDecryptMedia(message: MessageEntity): String? {
        val ctx = appContext ?: return null
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
            val cacheDir = File(ctx.cacheDir, "media_cache").apply { mkdirs() }
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
