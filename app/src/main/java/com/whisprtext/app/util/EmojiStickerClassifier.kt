package com.whisprtext.app.util

object EmojiStickerClassifier {

    /**
     * Checks if a string consists ONLY of Unicode emojis and whitespace.
     */
    fun isEmojiOnly(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        var emojiCount = 0
        var i = 0
        val len = trimmed.length

        while (i < len) {
            val codePoint = trimmed.codePointAt(i)
            val charCount = Character.charCount(codePoint)

            if (Character.isWhitespace(codePoint)) {
                i += charCount
                continue
            }

            if (isEmojiCodePoint(codePoint)) {
                emojiCount++
                i += charCount
            } else {
                return false
            }
        }

        return emojiCount > 0
    }

    /**
     * Counts the number of distinct emoji graphemes/code points in an emoji-only text string.
     */
    fun countEmojis(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0

        var count = 0
        var i = 0
        val len = trimmed.length

        while (i < len) {
            val codePoint = trimmed.codePointAt(i)
            val charCount = Character.charCount(codePoint)

            if (!Character.isWhitespace(codePoint) && isEmojiCodePoint(codePoint)) {
                count++
            }
            i += charCount
        }
        return count
    }

    /**
     * Checks if a message is a sticker-only message.
     */
    fun isStickerOnly(mimeType: String?, text: String?): Boolean {
        if (mimeType == null) return false
        val isStickerMime = mimeType.startsWith("sticker/") ||
                mimeType.endsWith("+sticker") ||
                mimeType == "application/json+lottie"
        val isTextEmptyOrPlaceholder = text.isNullOrBlank() || text == "[Sticker]" || text == "[Media]"
        return isStickerMime && isTextEmptyOrPlaceholder
    }

    /**
     * Helper to detect general emoji code point ranges, symbols, variation selectors, and ZWJ sequences.
     */
    private fun isEmojiCodePoint(codePoint: Int): Boolean {
        return (codePoint in 0x1F600..0x1F64F) || // Emoticons
                (codePoint in 0x1F300..0x1F5FF) || // Misc Symbols & Pictographs
                (codePoint in 0x1F680..0x1F6FF) || // Transport & Map
                (codePoint in 0x1F1E6..0x1F1FF) || // Regional Indicator Flags
                (codePoint in 0x2600..0x27BF)   || // Misc Symbols & Dingbats
                (codePoint in 0x1F900..0x1F9FF) || // Supplemental Symbols & Pictographs
                (codePoint in 0x1FA70..0x1FAFF) || // Symbols & Pictographs Extended-A
                (codePoint in 0x200D..0x200D)   || // Zero Width Joiner
                (codePoint in 0xFE00..0xFE0F)   || // Variation Selectors
                (codePoint in 0xE0020..0xE007F) || // Tags
                (codePoint in 0x1F004..0x1F0CF)    // Mahjong / Domino / Playing cards
    }
}
