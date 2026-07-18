package com.whisprtext.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whisprtext.app.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY COALESCE(lastMessageTime, createdAt) DESC")
    fun getConversationsFlow(): Flow<List<ConversationEntity>>

    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE id = :conversationId")
    suspend fun incrementUnreadCount(conversationId: String)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :conversationId")
    suspend fun clearUnreadCount(conversationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity): Unit

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<ConversationEntity>): Unit

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun getByIdFlow(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE (username = :username OR phoneNumber = :phoneNumber) AND type = 'direct' LIMIT 1")
    suspend fun getDirectConversationByContact(username: String?, phoneNumber: String?): ConversationEntity?

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    /** Propagate a contact's profile fields into matching direct conversations. */
    @Query(
        """
        UPDATE conversations
        SET avatarUrl = :avatarUrl,
            title = :displayName,
            username = :username,
            phoneNumber = :phoneNumber
        WHERE type = 'direct' AND username = :matchUsername
        """
    )
    suspend fun updateDirectConversationProfile(
        matchUsername: String,
        username: String,
        displayName: String,
        avatarUrl: String?,
        phoneNumber: String?
    )
}
