package com.whisprtext.app.data.repository

import com.whisprtext.app.data.local.AppDatabase
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.remote.model.ConversationSummaryDto
import com.whisprtext.app.data.remote.model.MessageDto
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatRepository(
    private val database: AppDatabase,
    private val apiClient: ApiClient
) {
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()

    fun getConversations(): Flow<List<ConversationEntity>> = conversationDao.getConversationsFlow()

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> = messageDao.getMessagesForConversation(conversationId)

    suspend fun syncConversations() {
        try {
            val remoteSummaries = apiClient.getConversations()
            val entities = remoteSummaries.map { it.toEntity() }
            conversationDao.insertAll(entities)

            // Insert latest messages if present
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
            syncStatus = "sending"
        )
        // Optimistic local insert
        messageDao.insert(localMsg)

        try {
            val response = apiClient.sendMessage(conversationId, content)
            // Replace temporary message with final persisted message
            messageDao.deleteById(tempId)
            messageDao.insert(response.toEntity("sent"))

            // Update conversation last message details
            val conv = conversationDao.getById(conversationId)
            if (conv != null) {
                conversationDao.insert(conv.copy(
                    lastMessageText = content,
                    lastMessageTime = response.createdAt.toEpochMillis()
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Mark sync failure
            messageDao.insert(localMsg.copy(syncStatus = "failed"))
        }
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
