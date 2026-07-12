package com.whisprtext.app.data.remote.model

data class UserDto(val id: String, val username: String)
data class DeviceDto(val id: String, val userId: String, val deviceName: String)

data class AuthResponse(
    val user: UserDto,
    val device: DeviceDto,
    val sessionToken: String
)

data class ConversationDto(
    val id: String,
    val type: String,
    val createdAt: String
)

data class ConversationSummaryDto(
    val id: String,
    val type: String,
    val createdAt: String,
    val unreadCount: Int,
    val lastMessage: MessageDto?
)

data class MessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderDeviceId: String,
    val encryptedContent: String,
    val createdAt: String
)

data class ReceiptDto(
    val messageId: String,
    val userId: String,
    val status: String,
    val updatedAt: String
)

data class DeltaSyncDto(
    val messages: List<MessageDto>,
    val receipts: List<ReceiptDto>,
    val currentTime: String
)
