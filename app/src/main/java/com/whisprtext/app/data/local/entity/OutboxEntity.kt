package com.whisprtext.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val clientMessageId: String,
    val conversationId: String,
    val recipientUserId: String,
    val recipientDeviceId: String,
    val ciphertextBase64: String,
    val ciphertextType: String,
    val protocolVersion: Int,
    val createdAt: Long,
    val status: String, // "pending", "queued", "sent", "failed"
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val expiresAt: Long? = null,
    val decryptedContent: String? = null,
    val localFilePath: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val attachmentUrl: String? = null,
    val encryptedKey: String? = null
)
