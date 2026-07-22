package com.whisprtext.app.translation

import com.whisprtext.app.data.local.dao.TranslationDao
import com.whisprtext.app.data.local.entity.TranslationEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class TranslationRepositoryTest {

    private lateinit var mockEngine: TranslationEngine
    private lateinit var mockDao: TranslationDao
    private lateinit var mockDetector: LanguageDetector
    private lateinit var repository: TranslationRepository

    @Before
    fun setup() {
        mockEngine = mock()
        mockDao = mock()
        mockDetector = mock()
        repository = TranslationRepository(
            translationEngine = mockEngine,
            translationDao = mockDao,
            languageDetector = mockDetector,
            modelVersion = "nllb-200-distilled-600m-int8-1.0.0"
        )
    }

    @Test
    fun testCacheHitReturnsCachedTranslation() = runTest {
        val cachedEntity = TranslationEntity(
            messageId = "msg_123",
            targetLanguage = "eng_Latn",
            sourceLanguage = "spa_Latn",
            translatedText = "How are you today?",
            modelVersion = "nllb-200-distilled-600m-int8-1.0.0"
        )
        whenever(mockDetector.detectLanguage(any())).thenReturn(
            LanguageDetectionResult("spa_Latn", 0.95f, isSupported = true, isAmbiguousOrTooShort = false)
        )
        whenever(mockDao.getTranslation("msg_123", "eng_Latn", "spa_Latn", "nllb-200-distilled-600m-int8-1.0.0")).thenReturn(cachedEntity)

        val result = repository.getOrTranslateMessage(
            messageId = "msg_123",
            text = "¿Cómo estás hoy?",
            targetLanguage = "eng_Latn"
        )

        assertTrue(result is TranslationResult.Success)
        val success = result as TranslationResult.Success
        assertEquals("How are you today?", success.translatedText)
        assertEquals("spa_Latn", success.sourceLanguage)
        verifyNoInteractions(mockEngine)
    }

    @Test
    fun testSameSourceAndTargetLanguageSkipsInference() = runTest {
        whenever(mockDetector.detectLanguage(any())).thenReturn(
            LanguageDetectionResult("eng_Latn", 0.95f, isSupported = true, isAmbiguousOrTooShort = false)
        )
        whenever(mockDao.getTranslation("msg_124", "eng_Latn", "eng_Latn", "nllb-200-distilled-600m-int8-1.0.0")).thenReturn(null)

        val result = repository.getOrTranslateMessage(
            messageId = "msg_124",
            text = "Hello world",
            targetLanguage = "eng_Latn"
        )

        assertTrue(result is TranslationResult.Success)
        val success = result as TranslationResult.Success
        assertEquals("Hello world", success.translatedText)
        assertEquals("eng_Latn", success.sourceLanguage)
        verifyNoInteractions(mockEngine)
    }

    @Test
    fun testEmptyInputSkipsTranslation() = runTest {
        val result = repository.getOrTranslateMessage(
            messageId = "msg_125",
            text = "   ",
            targetLanguage = "eng_Latn"
        )

        assertTrue(result is TranslationResult.Skipped)
        assertEquals("Empty text", (result as TranslationResult.Skipped).reason)
    }

    @Test
    fun testAmbiguousOrShortInputSkipsTranslation() = runTest {
        whenever(mockDetector.detectLanguage("OK")).thenReturn(
            LanguageDetectionResult(null, 0.2f, isSupported = false, isAmbiguousOrTooShort = true)
        )
        whenever(mockDao.getTranslation("msg_126", "eng_Latn", "eng_Latn", "nllb-200-distilled-600m-int8-1.0.0")).thenReturn(null)

        val result = repository.getOrTranslateMessage(
            messageId = "msg_126",
            text = "OK",
            targetLanguage = "eng_Latn"
        )

        assertTrue(result is TranslationResult.Skipped)
        assertEquals("Ambiguous or too short text", (result as TranslationResult.Skipped).reason)
    }

    @Test
    fun testCacheMissRunsInferenceAndInsertsToDb() = runTest {
        whenever(mockDetector.detectLanguage(any())).thenReturn(
            LanguageDetectionResult("spa_Latn", 0.95f, isSupported = true, isAmbiguousOrTooShort = false)
        )
        whenever(mockDao.getTranslation("msg_127", "eng_Latn", "spa_Latn", "nllb-200-distilled-600m-int8-1.0.0")).thenReturn(null)
        whenever(mockEngine.translate("¿Cómo estás hoy?", "spa_Latn", "eng_Latn"))
            .thenReturn(Result.success("How are you today?"))

        val result = repository.getOrTranslateMessage(
            messageId = "msg_127",
            text = "¿Cómo estás hoy?",
            targetLanguage = "eng_Latn"
        )

        assertTrue(result is TranslationResult.Success)
        val success = result as TranslationResult.Success
        assertEquals("How are you today?", success.translatedText)

        verify(mockDao).insertTranslation(any())
    }

    @Test
    fun testEngineFailureReturnsErrorResult() = runTest {
        whenever(mockDetector.detectLanguage(any())).thenReturn(
            LanguageDetectionResult("spa_Latn", 0.95f, isSupported = true, isAmbiguousOrTooShort = false)
        )
        whenever(mockDao.getTranslation("msg_128", "eng_Latn", "spa_Latn", "nllb-200-distilled-600m-int8-1.0.0")).thenReturn(null)
        whenever(mockEngine.translate(any(), any(), any()))
            .thenReturn(Result.failure(RuntimeException("ONNX model error")))

        val result = repository.getOrTranslateMessage(
            messageId = "msg_128",
            text = "¿Cómo estás hoy?",
            targetLanguage = "eng_Latn"
        )

        assertTrue(result is TranslationResult.Error)
        assertEquals("ONNX model error", (result as TranslationResult.Error).message)
    }
}
