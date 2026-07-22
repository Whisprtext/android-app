package com.whisprtext.app.translation

import com.whisprtext.app.data.local.dao.TranslationDao
import com.whisprtext.app.data.local.entity.TranslationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

sealed class TranslationResult {
    data class Success(
        val originalText: String,
        val translatedText: String,
        val sourceLanguage: String,
        val targetLanguage: String,
        val messageId: String = "",
        val modelVersion: String = "nllb-200-distilled-600m-int8-1.0.0"
    ) : TranslationResult()
    data class Skipped(val reason: String, val text: String) : TranslationResult()
    data class Error(val message: String, val throwable: Throwable? = null) : TranslationResult()
}

class TranslationRepository(
    private val translationEngine: TranslationEngine,
    private val translationDao: TranslationDao,
    private val languageDetector: LanguageDetector = LocalLanguageDetector(),
    private val modelVersion: String = "nllb-200-distilled-600m-int8-1.0.0"
) {

    companion object {
        private const val TAG = "TranslationRepository"

        private fun logI(msg: String) {
            try { android.util.Log.i(TAG, msg) } catch (_: Throwable) { println("INFO: [$TAG] $msg") }
        }

        fun postProcessTranslation(text: String): String {
            var cleaned = text.trim()
            // 1. Clean spacing around punctuation (e.g., "Hey , buddy ." -> "Hey, buddy.")
            cleaned = cleaned.replace("\\s+([.,!?:;])".toRegex(), "$1")
            // 2. Deduplicate repeated consecutive words (e.g., "Hey, hey, hey" -> "Hey.")
            for (i in 0..3) {
                cleaned = cleaned.replace("(?i)\\b(\\w+)\\b(?:\\s*[,;:]\\s*|\\s+)\\1\\b".toRegex(), "$1")
            }
            return cleaned.trim()
        }

        fun isEmojiOrSymbolOnly(text: String): Boolean {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return false
            return !trimmed.any { Character.isLetterOrDigit(it) }
        }

        fun preserveSpecialContent(original: String, translated: String): String {
            var result = translated
            // Preserve URLs
            val urlRegex = Regex("""https?://\S+""")
            val originalUrls = urlRegex.findAll(original).map { it.value }.toList()
            for (url in originalUrls) {
                if (!result.contains(url)) {
                    result = "$result $url".trim()
                }
            }
            // Preserve @mentions / usernames
            val mentionRegex = Regex("""@\w+""")
            val originalMentions = mentionRegex.findAll(original).map { it.value }.toList()
            for (mention in originalMentions) {
                if (!result.contains(mention)) {
                    result = "$result $mention".trim()
                }
            }
            return result
        }
    }

    suspend fun getOrTranslateMessage(
        messageId: String,
        text: String,
        targetLanguage: String,
        explicitSourceLanguage: String? = null
    ): TranslationResult = withContext(Dispatchers.IO) {
        val trimmed = text.trim()

        // 1. Skip check: empty or whitespace
        if (trimmed.isEmpty()) {
            return@withContext TranslationResult.Skipped("Empty text", text)
        }

        // Detect source language first to build exact cache lookup key
        val detection = languageDetector.detectLanguage(trimmed)
        val sourceLang = explicitSourceLanguage ?: detection.languageCode ?: LanguageCodeMapper.DEFAULT_NLLB_CODE

        // 2. Check local database cache first with exact (messageId, targetLanguage, sourceLanguage, modelVersion) key
        val cached = translationDao.getTranslation(messageId, targetLanguage, sourceLang, modelVersion)
        if (cached != null) {
            val cleanedText = postProcessTranslation(cached.translatedText)
            logI("[TranslationStart]\nmessageId=$messageId\ninputText=$text\nsourceLanguage=$sourceLang\ntargetLanguage=$targetLanguage\ncacheHit=true")
            val res = TranslationResult.Success(
                messageId = messageId,
                originalText = text,
                translatedText = cleanedText,
                sourceLanguage = cached.sourceLanguage,
                targetLanguage = cached.targetLanguage,
                modelVersion = cached.modelVersion
            )
            logI("[RepositoryResult]\nmessageId=$messageId\nreturnedTranslation=$cleanedText")
            return@withContext res
        }

        logI("[TranslationStart]\nmessageId=$messageId\ninputText=$text\nsourceLanguage=$sourceLang\ntargetLanguage=$targetLanguage\ncacheHit=false")

        // 3. Skip check: ambiguous/unsupported
        if (detection.isAmbiguousOrTooShort && explicitSourceLanguage == null) {
            return@withContext TranslationResult.Skipped("Ambiguous or too short text", text)
        }

        // 4. If source language matches target language, return original
        if (sourceLang.equals(targetLanguage, ignoreCase = true)) {
            val res = TranslationResult.Success(
                messageId = messageId,
                originalText = text,
                translatedText = text,
                sourceLanguage = sourceLang,
                targetLanguage = targetLanguage,
                modelVersion = modelVersion
            )
            logI("[RepositoryResult]\nmessageId=$messageId\nreturnedTranslation=$text")
            return@withContext res
        }

        // 5. Emoji or symbol only check: skip inference and preserve original text
        if (isEmojiOrSymbolOnly(trimmed)) {
            val res = TranslationResult.Success(
                messageId = messageId,
                originalText = text,
                translatedText = text,
                sourceLanguage = sourceLang,
                targetLanguage = targetLanguage,
                modelVersion = modelVersion
            )
            logI("[RepositoryResult]\nmessageId=$messageId\nreturnedTranslation=$text")
            return@withContext res
        }

        // 6. Run translation engine inference passing strictly isolated message text and language codes
        val engineToUse = translationEngine
        val result = if (engineToUse is NllbTranslationEngine) {
            engineToUse.translate(text, sourceLang, targetLanguage, messageId)
        } else {
            engineToUse.translate(text, sourceLang, targetLanguage)
        }

        if (result.isFailure) {
            val err = result.exceptionOrNull()
            return@withContext TranslationResult.Error(
                message = err?.message ?: "Translation failed",
                throwable = err
            )
        }

        val rawTranslatedText = result.getOrThrow()
        val postProcessed = postProcessTranslation(rawTranslatedText)
        val translatedText = preserveSpecialContent(text, postProcessed)

        // 7. Save to local Room database cache
        val entity = TranslationEntity(
            messageId = messageId,
            targetLanguage = targetLanguage,
            sourceLanguage = sourceLang,
            translatedText = translatedText,
            modelVersion = modelVersion,
            createdAt = System.currentTimeMillis()
        )
        translationDao.insertTranslation(entity)

        val finalRes = TranslationResult.Success(
            messageId = messageId,
            originalText = text,
            translatedText = translatedText,
            sourceLanguage = sourceLang,
            targetLanguage = targetLanguage,
            modelVersion = modelVersion
        )
        logI("[RepositoryResult]\nmessageId=$messageId\nreturnedTranslation=$translatedText")
        finalRes
    }

    fun observeTranslation(messageId: String, targetLanguage: String): Flow<TranslationEntity?> {
        return translationDao.observeTranslation(messageId, targetLanguage)
    }

    suspend fun clearTranslationsForMessage(messageId: String) {
        withContext(Dispatchers.IO) {
            translationDao.deleteTranslationsForMessage(messageId)
        }
    }

    suspend fun clearAllTranslations() {
        withContext(Dispatchers.IO) {
            translationDao.clearAllTranslations()
        }
    }
}
