package com.whisprtext.app.crypto

object Base64Compat {
    fun encode(bytes: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (e: Throwable) {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    }

    fun decode(str: String): ByteArray {
        val cleaned = str.replace("\n", "").replace("\r", "").trim()
        return try {
            java.util.Base64.getDecoder().decode(cleaned)
        } catch (e: Throwable) {
            android.util.Base64.decode(cleaned, android.util.Base64.NO_WRAP)
        }
    }
}
