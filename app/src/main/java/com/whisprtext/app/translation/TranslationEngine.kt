package com.whisprtext.app.translation

interface TranslationEngine {
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): Result<String>
}
