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

    init {
        // Collect real-time events from WebSocketManager
        scope.launch {
            webSocketManager.events.collect { event ->
                when (event) {
                    is WebSocketEvent.NewMessage -> {
                        val currentUserId = preferencesManager.userId.first()
                        if (event.message.senderId != currentUserId) {
                            webSocketManager.markMessageDelivered(event.message.id)
                        }

                        val entityStatus = if (event.message.senderId == currentUserId) "sent" else "delivered"
                        messageDao.insert(event.message.toEntity(entityStatus))
                        
                        val conv = conversationDao.getById(event.message.conversationId)
                        if (conv != null) {
                            conversationDao.insert(conv.copy(
                                lastMessageText = event.message.encryptedContent,
                                lastMessageTime = event.message.createdAt.toEpochMillis()
                            ))
                        }
                    }
                    is WebSocketEvent.Ack -> {
                        messageDao.deleteById(event.clientMsgId)
                    }
                    is WebSocketEvent.ReceiptUpdate -> {
                        val currentUserId = preferencesManager.userId.first()
                        if (event.receipt.userId != currentUserId) {
                            updateMessageStatus(event.receipt.messageId, event.receipt.status)
                        }
                    }
                    else -> {}
                }
            }
        }

        // Monitor internet connectivity to trigger resync & send retries
        scope.launch {
            var wasOffline = false
            networkMonitor.isOnline.collect { isOnline ->
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
                messageDao.insertAll(latestMessages)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncMessages(conversationId: String) {
        try {
            val remoteMessages = apiClient.getMessages(conversationId)
            val entities = remoteMessages.map { it.toEntity("sent") }
            messageDao.insertAll(entities)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadOlderMessages(conversationId: String, oldestMessageTime: Long, limit: Int = 20) {
        try {
            val cursor = java.time.Instant.ofEpochMilli(oldestMessageTime).toString()
            val remoteMessages = apiClient.getMessages(conversationId, cursor = cursor, direction = "older", limit = limit)
            val entities = remoteMessages.map { it.toEntity("sent") }
            messageDao.insertAll(entities)
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
            messageDao.insert(response.toEntity("sent"))

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
            val delta = apiClient.sync(since)
            val currentUserId = preferencesManager.userId.first()
            
            if (delta.messages.isNotEmpty()) {
                val entities = delta.messages.map { 
                    val status = if (it.senderId == currentUserId) "sent" else "delivered"
                    it.toEntity(status) 
                }
                messageDao.insertAll(entities)
                
                for (msg in delta.messages) {
                    if (msg.senderId != currentUserId) {
                        webSocketManager.markMessageDelivered(msg.id)
                    }
                }
                
                val grouped = delta.messages.groupBy { it.conversationId }
                for ((convId, msgs) in grouped) {
                    val latest = msgs.maxByOrNull { it.createdAt.toEpochMillis() }
                    val conv = conversationDao.getById(convId)
                    if (conv != null && latest != null) {
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
                    messageDao.insert(response.toEntity("sent"))

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
        val existing = messageDao.getById(messageId) ?: return
        if (getStatusPrecedence(newStatus) > getStatusPrecedence(existing.syncStatus)) {
            messageDao.insert(existing.copy(syncStatus = newStatus))
        }
    }

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
            lastMessageTime = null
        )
        conversationDao.insert(entity)
        return entity
    }

    suspend fun searchUserByUsername(username: String) = apiClient.searchUserByUsername(username)
    suspend fun lookupUsersByPhone(phoneNumbers: List<String>) = apiClient.lookupUsersByPhone(phoneNumbers)
    suspend fun updateSettings(
        phoneNumber: String?,
        discoverableByUsername: Boolean,
        discoverableByPhone: Boolean
    ) = apiClient.updateSettings(phoneNumber, discoverableByUsername, discoverableByPhone)

    // Mapper utilities
    private fun ConversationSummaryDto.toEntity(): ConversationEntity {
        return ConversationEntity(
            id = id,
            type = type,
            createdAt = createdAt.toEpochMillis(),
            unreadCount = unreadCount,
            lastMessageText = lastMessage?.encryptedContent,
            lastMessageTime = lastMessage?.createdAt?.toEpochMillis()
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
}
