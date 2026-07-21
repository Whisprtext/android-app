package com.whisprtext.app

import com.whisprtext.app.crypto.LocalEncryptor
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.util.EmojiStickerClassifier
import org.junit.Assert.*
import org.junit.Test

class AnimatedEmojiStickerTest {

    @Test
    fun testEmojiOnlyMessageBubbleClassification() {
        val emojiMessage = MessageEntity(
            id = "msg1",
            conversationId = "conv1",
            senderId = "user1",
            senderDeviceId = "dev1",
            encryptedContent = LocalEncryptor.encrypt("😀"),
            createdAt = System.currentTimeMillis(),
            syncStatus = "sent"
        )

        assertTrue(EmojiStickerClassifier.isEmojiOnly(emojiMessage.decryptedContent))
        assertFalse(EmojiStickerClassifier.isStickerOnly(emojiMessage.mimeType, emojiMessage.decryptedContent))
    }

    @Test
    fun testStickerOnlyMessageBubbleClassification() {
        val stickerMessage = MessageEntity(
            id = "msg2",
            conversationId = "conv1",
            senderId = "user1",
            senderDeviceId = "dev1",
            encryptedContent = LocalEncryptor.encrypt("[Sticker]"),
            createdAt = System.currentTimeMillis(),
            syncStatus = "sent",
            mimeType = "application/json+lottie",
            attachmentUrl = "https://example.com/sticker.json"
        )

        assertFalse(EmojiStickerClassifier.isEmojiOnly(stickerMessage.decryptedContent))
        assertTrue(EmojiStickerClassifier.isStickerOnly(stickerMessage.mimeType, stickerMessage.decryptedContent))
    }

    @Test
    fun testMixedMessageClassification() {
        val mixedMessage = MessageEntity(
            id = "msg3",
            conversationId = "conv1",
            senderId = "user1",
            senderDeviceId = "dev1",
            encryptedContent = LocalEncryptor.encrypt("Great job! 🎉"),
            createdAt = System.currentTimeMillis(),
            syncStatus = "sent"
        )

        assertFalse(EmojiStickerClassifier.isEmojiOnly(mixedMessage.decryptedContent))
        assertFalse(EmojiStickerClassifier.isStickerOnly(mixedMessage.mimeType, mixedMessage.decryptedContent))
    }
}
