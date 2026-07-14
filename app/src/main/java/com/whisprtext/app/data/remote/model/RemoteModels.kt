package com.whisprtext.app.data.remote.model

import com.google.gson.annotations.SerializedName

data class UserDto(
    val id: String,
    val username: String,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("discoverable_by_username") val discoverableByUsername: Boolean = true,
    @SerializedName("discoverable_by_phone") val discoverableByPhone: Boolean = true,
    @SerializedName("display_name") val displayName: String = "",
    val bio: String = "",
    @SerializedName("avatar_url") val avatarUrl: String = "",
    @SerializedName("phone_number_visibility") val phoneNumberVisibility: String = "everyone"
)
data class DeviceDto(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("device_name") val deviceName: String
)

data class PhoneLookupRequest(
    @SerializedName("phone_numbers") val phoneNumbers: List<String>
)

data class UpdateSettingsRequest(
    @SerializedName("phone_number") val phoneNumber: String?,
    @SerializedName("discoverable_by_username") val discoverableByUsername: Boolean,
    @SerializedName("discoverable_by_phone") val discoverableByPhone: Boolean,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("phone_number_visibility") val phoneNumberVisibility: String? = null
)

data class UpdateProfileRequest(
    val username: String,
    @SerializedName("display_name") val displayName: String,
    val bio: String,
    @SerializedName("avatar_url") val avatarUrl: String
)
data class MeResponse(
    val user: UserDto,
    val device: DeviceDto
)

data class AuthResponse(
    val user: UserDto,
    val device: DeviceDto,
    @SerializedName("session_token") val sessionToken: String
)

data class ConversationDto(
    val id: String,
    val type: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("display_name") val displayName: String? = null,
    val username: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null
)

data class ConversationSummaryDto(
    val id: String,
    val type: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("unread_count") val unreadCount: Int,
    @SerializedName("last_message") val lastMessage: MessageDto?,
    @SerializedName("display_name") val displayName: String? = null,
    val username: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null
)

data class MessageDto(
    val id: String,
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("sender_device_id") val senderDeviceId: String,
    @SerializedName("encrypted_content") val encryptedContent: String,
    @SerializedName("created_at") val createdAt: String
)

data class ReceiptDto(
    @SerializedName("message_id") val messageId: String,
    @SerializedName("user_id") val userId: String,
    val status: String,
    @SerializedName("updated_at") val updatedAt: String
)

data class DeltaSyncDto(
    val messages: List<MessageDto>,
    val receipts: List<ReceiptDto>,
    @SerializedName("deleted_message_ids") val deletedMessageIds: List<String> = emptyList(),
    @SerializedName("current_time") val currentTime: String
)

