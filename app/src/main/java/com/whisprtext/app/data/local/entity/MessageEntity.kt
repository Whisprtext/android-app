package com.whisprtext.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val senderDeviceId: String,
    val encryptedContent: String,
    val createdAt: Long,
    val syncStatus: String, // "pending", "sent", "failed", "delivered", "read"
    val attachmentUrl: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val encryptedKey: String? = null,
    val localFilePath: String? = null
)
