package com.whisprtext.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whisprtext.app.data.local.entity.PendingReceiptEntity

/**
 * DAO for the pending_receipts queue. Operations are used by ChatRepository to
 * enqueue, process, and retire outgoing delivery/read receipt confirmations.
 */
@Dao
interface PendingReceiptDao {

    /**
     * Insert a new pending receipt. REPLACE strategy means if a receipt for the
     * same primary key exists it will be overwritten (shouldn't happen in practice
     * since IDs are UUIDs, but it is a safety net).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(receipt: PendingReceiptEntity)

    /**
     * Return all pending receipts ordered oldest-first so we process them in the
     * order they were created and maintain causal consistency.
     */
    @Query("SELECT * FROM pending_receipts ORDER BY createdAtMs ASC")
    suspend fun getAll(): List<PendingReceiptEntity>

    /**
     * Remove a pending receipt once it has been successfully delivered to the
     * backend or superseded by a higher-priority receipt (e.g. "read" > "delivered").
     */
    @Query("DELETE FROM pending_receipts WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Remove all pending receipts for a given message — used when a "read" receipt
     * supersedes any previously queued "delivered" receipt for the same message.
     */
    @Query("DELETE FROM pending_receipts WHERE messageId = :messageId")
    suspend fun deleteByMessageId(messageId: String)

    /**
     * Increment the attempt counter for a receipt entry (called on transient failure
     * so we can implement back-off and eventually abandon stale entries).
     */
    @Query("UPDATE pending_receipts SET attempts = attempts + 1 WHERE id = :id")
    suspend fun incrementAttempts(id: String)

    /**
     * Remove entries that have exceeded the maximum retry threshold to prevent
     * stale receipts from being retried indefinitely.
     */
    @Query("DELETE FROM pending_receipts WHERE attempts >= :maxAttempts")
    suspend fun deleteExhausted(maxAttempts: Int)
}
