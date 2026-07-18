package com.whisprtext.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val type: String, // "direct", "group"
    val createdAt: Long,
    val unreadCount: Int,
    val lastMessageText: String?,
    val lastMessageTime: Long?,
    val title: String? = null,
    val username: String? = null,
    val phoneNumber: String? = null,
    val avatarUrl: String? = null,
    val gradientStartColor: Int? = null,
    val gradientEndColor: Int? = null
)
