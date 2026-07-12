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
    val syncStatus: String // "sending", "sent", "failed"
)
