package com.whisprtext.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted queue entry for a delivery or read receipt that has not yet been
 * successfully acknowledged by the backend. Entries are inserted when a receipt
 * needs to be sent and deleted once the server confirms receipt (HTTP 200).
 *
 * Using Room ensures the queue survives process death and can be retried on next
 * app launch or when connectivity is restored.
 */
@Entity(tableName = "pending_receipts")
data class PendingReceiptEntity(
    /** Unique ID for this pending receipt entry (auto-generated UUID). */
    @PrimaryKey val id: String,
    /** The message whose receipt we need to report. */
    val messageId: String,
    /** The conversation the message belongs to (for context). */
    val conversationId: String,
    /**
     * Receipt status to be sent: "delivered" or "read".
     * "read" supersedes "delivered" so older "delivered" entries for the same
     * message can be deleted when a "read" entry is queued.
     */
    val status: String,
    /** Epoch millis when this pending entry was created — used for ordering. */
    val createdAtMs: Long = System.currentTimeMillis(),
    /** Number of send attempts so far (for exponential back-off / abandonment). */
    val attempts: Int = 0,
)
