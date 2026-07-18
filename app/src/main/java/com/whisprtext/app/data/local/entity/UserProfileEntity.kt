package com.whisprtext.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.whisprtext.app.data.remote.model.UserDto

/**
 * Local cache of user profiles (self + contacts).
 * Used for fast profile screens, chat headers, and avatars without a network round-trip.
 */
@Entity(
    tableName = "user_profiles",
    indices = [Index(value = ["username"], unique = true)]
)
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val phoneNumber: String? = null,
    val phoneNumberVisibility: String = "everyone",
    val discoverableByUsername: Boolean = true,
    val discoverableByPhone: Boolean = true,
    val isSelf: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toUserDto(): UserDto = UserDto(
        id = id,
        username = username,
        phoneNumber = phoneNumber,
        discoverableByUsername = discoverableByUsername,
        discoverableByPhone = discoverableByPhone,
        displayName = displayName,
        bio = bio,
        avatarUrl = avatarUrl,
        phoneNumberVisibility = phoneNumberVisibility
    )

    companion object {
        fun fromUserDto(user: UserDto, isSelf: Boolean = false): UserProfileEntity =
            UserProfileEntity(
                id = user.id,
                username = user.username,
                displayName = user.displayName,
                bio = user.bio,
                avatarUrl = user.avatarUrl,
                phoneNumber = user.phoneNumber,
                phoneNumberVisibility = user.phoneNumberVisibility,
                discoverableByUsername = user.discoverableByUsername,
                discoverableByPhone = user.discoverableByPhone,
                isSelf = isSelf,
                updatedAt = System.currentTimeMillis()
            )
    }
}
