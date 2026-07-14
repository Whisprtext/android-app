package com.whisprtext.app.data.repository

import com.whisprtext.app.data.local.AppDatabase
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.remote.WebSocketEvent
import com.whisprtext.app.data.remote.WebSocketManager
import com.whisprtext.app.data.remote.model.ConversationSummaryDto
import com.whisprtext.app.data.remote.model.MessageDto
import com.whisprtext.app.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChatRepository(
    private val database: AppDatabase,
    private val apiClient: ApiClient,
    private val webSocketManager: WebSocketManager,
    private val networkMonitor: NetworkMonitor,
    private val preferencesManager: PreferencesManager,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) {
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val scope = CoroutineScope(ioDispatcher)
    private var cachedUserId: String? = null
    private val pendingReceipts = ConcurrentHashMap<String, String>()

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
                            if (event.message.senderId != currentUserId) {
                                webSocketManager?.markMessageDelivered(event.message.id)
                            }

                            val entityStatus = if (event.message.senderId == currentUserId) "sent" else "delivered"
                            upsertMessage(event.message.toEntity(entityStatus))
                            
                            val conv = conversationDao.getById(event.message.conversationId)
                            if (conv != null) {
                                conversationDao.insert(conv.copy(
                                    lastMessageText = event.message.encryptedContent,
                                    lastMessageTime = event.message.createdAt.toEpochMillis()
                                ))
                            } else {
                                scope.launch {
                                    syncConversations()
                                }
                            }
                        }
                        is WebSocketEvent.Ack -> {
                            messageDao.deleteById(event.clientMsgId)
                        }
                        is WebSocketEvent.ReceiptUpdate -> {
                            if (event.receipt.userId != currentUserId) {
                                updateMessageStatus(event.receipt.messageId, event.receipt.status)
                            }
                        }
                        is WebSocketEvent.MessageDeleted -> {
                            messageDao.deleteById(event.messageId)
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
                            syncDelta()
                            retryFailedMessages()
                        } else {
                            // Resync when initialized while online
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

            val latestMessages = remoteSummaries.mapNotNull { it.lastMessage?.toEntity("sent") }
            if (latestMessages.isNotEmpty()) {
                upsertMessages(latestMessages)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncMessages(conversationId: String) {
        try {
            val remoteMessages = apiClient.getMessages(conversationId)
            val entities = remoteMessages.map { it.toEntity("sent") }
            upsertMessages(entities)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            // On first launch or after reinstall, since is null — fetch all messages & conversations.
            if (since == null) {
                syncConversations()
            }
            val delta = apiClient.sync(since)
            val currentUserId = preferencesManager.userId.first()

            if (delta.messages.isNotEmpty()) {
                val entities = delta.messages.map {
                    val status = if (it.senderId == currentUserId) "sent" else "delivered"
                    it.toEntity(status)
                }
                upsertMessages(entities)

                for (msg in delta.messages) {
                    if (msg.senderId != currentUserId) {
                        webSocketManager.markMessageDelivered(msg.id)
                    }
                }

                val grouped = delta.messages.groupBy { it.conversationId }
                for ((convId, msgs) in grouped) {
                    val latest = msgs.maxByOrNull { it.createdAt.toEpochMillis() }
                    val conv = conversationDao.getById(convId)
                    if (conv == null) {
                        // Re-discover any conversation we don't have locally.
                        syncConversations()
                        break
                    } else if (latest != null) {
                        conversationDao.insert(conv.copy(
                            lastMessageText = latest.encryptedContent,
                            lastMessageTime = latest.createdAt.toEpochMillis()
                        ))
                    }
                }
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

    suspend fun retryFailedMessages() {
        try {
            val failedList = messageDao.getMessagesBySyncStatus("failed")
            for (msg in failedList) {
                try {
                    messageDao.insert(msg.copy(syncStatus = "pending"))
                    val response = apiClient.sendMessage(msg.conversationId, msg.encryptedContent)
                    messageDao.deleteById(msg.id)
                    upsertMessage(response.toEntity("sent"))

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
        webSocketManager.markConversationRead(conversationId)
        messageDao.markConversationMessagesRead(conversationId, currentUserId)
    }

    suspend fun createConversation(type: String, members: List<String>): ConversationEntity {
        val response = apiClient.createConversation(type, members)
        val entity = ConversationEntity(
            id = response.id,
            type = response.type,
            createdAt = response.createdAt.toEpochMillis(),
            unreadCount = 0,
            lastMessageText = null,
            lastMessageTime = null,
            title = response.displayName ?: response.username,
            username = response.username,
            phoneNumber = response.phoneNumber
        )
        conversationDao.insert(entity)
        return entity
    }

    suspend fun createDirectConversation(targetUserId: String?, username: String?): ConversationEntity {
        val response = apiClient.createDirectConversation(targetUserId, username)
        val entity = ConversationEntity(
            id = response.id,
            type = response.type,
            createdAt = response.createdAt.toEpochMillis(),
            unreadCount = 0,
            lastMessageText = null,
            lastMessageTime = null,
            title = response.displayName ?: response.username ?: targetUserId ?: username,
            username = response.username,
            phoneNumber = response.phoneNumber
        )
        conversationDao.insert(entity)
        return entity
    }

    suspend fun searchUserByUsername(username: String) = apiClient.searchUserByUsername(username)
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
            phoneNumber = phoneNumber
        )
    }

    private fun MessageDto.toEntity(status: String): MessageEntity {
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            senderDeviceId = senderDeviceId,
            encryptedContent = encryptedContent,
            createdAt = createdAt.toEpochMillis(),
            syncStatus = status
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
}
