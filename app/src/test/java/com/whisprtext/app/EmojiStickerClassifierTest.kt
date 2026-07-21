package com.whisprtext.app

import com.whisprtext.app.util.EmojiStickerClassifier
import org.junit.Assert.*
import org.junit.Test

class EmojiStickerClassifierTest {

    @Test
    fun testSingleEmojiOnly() {
        val text = "😀"
        assertTrue(EmojiStickerClassifier.isEmojiOnly(text))
        assertEquals(1, EmojiStickerClassifier.countEmojis(text))
    }

    @Test
    fun testMultipleEmojiOnly() {
        val text = "😀 🎉 🔥"
        assertTrue(EmojiStickerClassifier.isEmojiOnly(text))
        assertEquals(3, EmojiStickerClassifier.countEmojis(text))
    }

    @Test
    fun testMixedTextAndEmoji() {
        val text = "Hello 😀 world!"
        assertFalse(EmojiStickerClassifier.isEmojiOnly(text))
    }

    @Test
    fun testRegularTextOnly() {
        val text = "Hello world"
        assertFalse(EmojiStickerClassifier.isEmojiOnly(text))
        assertEquals(0, EmojiStickerClassifier.countEmojis(text))
    }

    @Test
    fun testStickerOnlyClassification() {
        assertTrue(EmojiStickerClassifier.isStickerOnly("application/json+lottie", "[Sticker]"))
        assertTrue(EmojiStickerClassifier.isStickerOnly("sticker/webp", null))
        assertFalse(EmojiStickerClassifier.isStickerOnly("image/jpeg", "[Media]"))
    }
}
