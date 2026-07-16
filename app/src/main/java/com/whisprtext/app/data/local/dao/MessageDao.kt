package com.whisprtext.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whisprtext.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("UPDATE messages SET syncStatus = 'read' WHERE conversationId = :conversationId AND senderId != :currentUserId AND syncStatus != 'read'")
    suspend fun markConversationMessagesRead(conversationId: String, currentUserId: String): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>): Unit

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String): Unit

    @Query("SELECT * FROM messages WHERE syncStatus = :status")
    suspend fun getMessagesBySyncStatus(status: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): MessageEntity?

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversationId(conversationId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    /**
     * Returns messages that were received (sender != currentUser) but not yet
     * marked as 'read' in the local cache. Used on startup to detect receipts
     * we may have failed to send during a previous session.
     */
    @Query(
        "SELECT * FROM messages WHERE senderId != :currentUserId AND syncStatus != 'read' ORDER BY createdAt ASC"
    )
    suspend fun getUnreadReceivedMessages(currentUserId: String): List<MessageEntity>
}

