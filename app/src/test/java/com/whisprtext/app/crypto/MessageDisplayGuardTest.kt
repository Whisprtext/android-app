package com.whisprtext.app.crypto

import com.whisprtext.app.data.local.entity.MessageEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Ensures MessageEntity / display path never surfaces Signal transport ciphertext.
 */
class MessageDisplayGuardTest {

    @Before
    fun setUp() {
        LocalEncryptor.isEncryptionEnabled = false
    }

    @Test
    fun failedStatus_showsGenericMessage() {
        val entity = MessageEntity(
            id = "1",
            conversationId = "c",
            senderId = "s",
            senderDeviceId = "d",
            encryptedContent = "D0IylT3OyLZ6gPSw2dAHftdeW5gjTB5odBdSP097pr4H0+Jpd/j0jVNI5b/Ht9CLxgmyU=",
            createdAt = 1L,
            syncStatus = "delivered",
            decryptionStatus = "failed"
        )
        assertEquals(SignalKeyManager.DISPLAY_DECRYPT_FAILED, entity.decryptedContent)
        assertTrue(entity.isDecryptionFailed)
        assertFalse(entity.decryptedContent.contains("D0Iyl"))
    }

    @Test
    fun ciphertextStoredAsContent_blockedByGuard() {
        LocalEncryptor.isEncryptionEnabled = false
        val cipher = "D0IylT3OyLZ6gPSw2dAHftdeW5gjTB5odBdSP097pr4H0+Jpd/j0jVNI5b/Ht9CLxgmyU="
        val entity = MessageEntity(
            id = "2",
            conversationId = "c",
            senderId = "s",
            senderDeviceId = "d",
            encryptedContent = cipher,
            createdAt = 1L,
            syncStatus = "delivered",
            decryptionStatus = "decrypted" // mis-marked — still blocked by isLikelyCiphertext
        )
        assertEquals(SignalKeyManager.DISPLAY_DECRYPT_FAILED, entity.decryptedContent)
    }

    @Test
    fun plaintext_displaysNormally() {
        LocalEncryptor.isEncryptionEnabled = false
        val entity = MessageEntity(
            id = "3",
            conversationId = "c",
            senderId = "s",
            senderDeviceId = "d",
            encryptedContent = "Hello friend",
            createdAt = 1L,
            syncStatus = "sent",
            decryptionStatus = "decrypted"
        )
        assertEquals("Hello friend", entity.decryptedContent)
        assertFalse(entity.isDecryptionFailed)
    }

    @Test
    fun legacyDecryptionFailedMarker_normalized() {
        LocalEncryptor.isEncryptionEnabled = false
        val entity = MessageEntity(
            id = "4",
            conversationId = "c",
            senderId = "s",
            senderDeviceId = "d",
            encryptedContent = "[Decryption failed]",
            createdAt = 1L,
            syncStatus = "delivered",
            decryptionStatus = "decrypted"
        )
        assertEquals(SignalKeyManager.DISPLAY_DECRYPT_FAILED, entity.decryptedContent)
    }
}
