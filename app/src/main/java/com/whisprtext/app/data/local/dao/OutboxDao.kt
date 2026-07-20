package com.whisprtext.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whisprtext.app.data.local.entity.OutboxEntity

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE status IN ('pending', 'queued') ORDER BY createdAt ASC")
    suspend fun getPending(): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE clientMessageId = :clientMessageId")
    suspend fun getByClientMessageId(clientMessageId: String): OutboxEntity?

    @Query("UPDATE outbox SET status = :status, attemptCount = attemptCount + 1, lastAttemptAt = :lastAttemptAt WHERE clientMessageId = :clientMessageId")
    suspend fun updateStatus(clientMessageId: String, status: String, lastAttemptAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM outbox WHERE clientMessageId = :clientMessageId")
    suspend fun deleteByClientMessageId(clientMessageId: String)

    @Query("DELETE FROM outbox WHERE status = 'sent' AND lastAttemptAt IS NOT NULL AND lastAttemptAt < :threshold")
    suspend fun deleteOldSent(threshold: Long)

    @Query("SELECT * FROM outbox WHERE status = 'failed' ORDER BY createdAt ASC")
    suspend fun getFailed(): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox WHERE status IN ('pending', 'queued')")
    suspend fun countPending(): Int
}
