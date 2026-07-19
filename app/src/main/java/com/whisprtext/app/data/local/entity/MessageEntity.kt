package com.whisprtext.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.whisprtext.app.crypto.SignalKeyManager

/**
 * Local message row. [encryptedContent] always holds LocalEncryptor-wrapped *display*
 * plaintext (never Signal transport ciphertext). Signal ciphertext is transport-only.
 *
 * [decryptionStatus]:
 * - "pending" — outbound not yet confirmed / inbound awaiting decrypt
 * - "decrypted" — plaintext available for UI
 * - "failed" — permanent local decrypt failure (generic UI status only)
 */
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
    val localFilePath: String? = null,
    /** pending | decrypted | failed */
    val decryptionStatus: String = "decrypted"
) {
    val decryptedContent: String
        get() {
            if (decryptionStatus == "failed") {
                return SignalKeyManager.DISPLAY_DECRYPT_FAILED
            }
            val plain = com.whisprtext.app.crypto.LocalEncryptor.decrypt(encryptedContent)
            // Never surface transport ciphertext even if LocalEncryptor falls open
            if (SignalKeyManager.isLikelyCiphertext(plain)) {
                return SignalKeyManager.DISPLAY_DECRYPT_FAILED
            }
            if (plain == "[Decryption failed]") {
                return SignalKeyManager.DISPLAY_DECRYPT_FAILED
            }
            return plain
        }

    val isDecryptionFailed: Boolean
        get() = decryptionStatus == "failed" ||
            decryptedContent == SignalKeyManager.DISPLAY_DECRYPT_FAILED
}
