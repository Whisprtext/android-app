package com.whisprtext.app.translation

import android.content.Context
import com.whisprtext.app.data.local.dao.TranslationDao
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.local.entity.TranslationEntity
import com.whisprtext.app.ui.viewmodel.ChatViewModel
import com.whisprtext.app.ui.viewmodel.MessageTranslationState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.io.File

class TranslationPipelineDiagnosticTest {

    private lateinit var modelDir: File
    private lateinit var mockContext: Context
    private lateinit var realEngine: NllbTranslationEngine
    private lateinit var mockDao: TranslationDao
    private lateinit var detector: LocalLanguageDetector
    private lateinit var repository: TranslationRepository

    @Before
    fun setup() {
        val rootAppDir = File("").absoluteFile.parentFile?.parentFile ?: File("C:/Users/nilan/Projects/whisprtext-app")
        modelDir = File(rootAppDir, "translation/models/nllb-int8")

        mockContext = mock()
        mockDao = mock()
        realEngine = NllbTranslationEngine(mockContext) { modelDir }
        detector = LocalLanguageDetector()

        repository = TranslationRepository(
            translationEngine = realEngine,
            translationDao = mockDao,
            languageDetector = detector,
            modelVersion = "nllb-200-distilled-600m-int8-1.0.0"
        )
    }

    @Test
    fun testA_ChineseTranslationWithNFKCNormalization() = runBlocking {
        val input = "很好，你呢？"
        val result = repository.getOrTranslateMessage(
            messageId = "test_a_zh",
            text = input,
            targetLanguage = "eng_Latn",
            explicitSourceLanguage = "zho_Hans"
        )

        assertTrue("Chinese translation must succeed", result is TranslationResult.Success)
        val success = result as TranslationResult.Success
        println("Test A (Chinese) Output: ${success.translatedText}")

        assertTrue(
            "Chinese translation must express good/how about you instead of corrupting fullwidth punctuation",
            success.translatedText.contains("good", ignoreCase = true) || success.translatedText.contains("how about you", ignoreCase = true)
        )
        assertFalse(
            "Chinese translation must NOT output incorrect string 'It's nice to meet you.'",
            success.translatedText.equals("It's nice to meet you.", ignoreCase = true)
        )
    }

    @Test
    fun testB_VietnameseLanguageDetectionAndTranslation() = runBlocking {
        val input = "Chúc một ngày tốt lành"

        // 1. Check Language Detector output
        val detection = detector.detectLanguage(input)
        val nllbCode = detection.languageCode ?: ""
        val displayName = LanguageCodeMapper.getDisplayNameForNllbCode(nllbCode)
        val isoCode = LanguageCodeMapper.getNllbCodeForLanguageCode("vi")

        println("Test B (Vietnamese) Detection: NLLB code=$nllbCode, displayName=$displayName, isoCode=$isoCode")

        assertEquals("Vietnamese ISO code 'vi' must resolve to vie_Latn", "vie_Latn", isoCode)
        assertEquals("Detector must classify Vietnamese text as vie_Latn", "vie_Latn", nllbCode)
        assertEquals("Display name for vie_Latn must be Vietnamese", "Vietnamese", displayName)
        assertFalse("Vietnamese text must NOT be detected as French", nllbCode == "fra_Latn")

        // 2. Check Repository translation output
        val result = repository.getOrTranslateMessage(
            messageId = "test_b_vi",
            text = input,
            targetLanguage = "eng_Latn"
        )

        assertTrue("Vietnamese translation must succeed", result is TranslationResult.Success)
        val success = result as TranslationResult.Success
        println("Test B (Vietnamese) Output: ${success.translatedText}")

        assertEquals("Source language in result must be vie_Latn", "vie_Latn", success.sourceLanguage)
        assertTrue("Output should mean 'Have a good day.'", success.translatedText.contains("good day", ignoreCase = true))
    }

    @Test
    fun testC_GermanTranslation() = runBlocking {
        val input = "Hallo"
        val result = repository.getOrTranslateMessage(
            messageId = "test_c_de",
            text = input,
            targetLanguage = "eng_Latn",
            explicitSourceLanguage = "deu_Latn"
        )

        assertTrue("German translation must succeed", result is TranslationResult.Success)
        val success = result as TranslationResult.Success
        println("Test C (German) Output: ${success.translatedText}")
        assertTrue("Output must contain text", success.translatedText.isNotBlank())
    }

    @Test
    fun testD_FrenchTranslation() = runBlocking {
        val input = "Tu fais quoi en ce moment ?"
        val result = repository.getOrTranslateMessage(
            messageId = "test_d_fr",
            text = input,
            targetLanguage = "eng_Latn",
            explicitSourceLanguage = "fra_Latn"
        )

        assertTrue("French translation must succeed", result is TranslationResult.Success)
        val success = result as TranslationResult.Success
        println("Test D (French) Output: ${success.translatedText}")
        assertTrue("Output should be 'What are you doing right now?'", success.translatedText.contains("doing", ignoreCase = true))
    }

    @Test
    fun testLowConfidenceDetectionDoesNotDefaultSilently() {
        val ambiguousInput = "k"
        val detection = detector.detectLanguage(ambiguousInput)

        println("Low Confidence Test: $ambiguousInput -> confidence=${detection.confidence}, isAmbiguous=${detection.isAmbiguousOrTooShort}")
        assertTrue("Short ambiguous word must be flagged as ambiguous/too short", detection.isAmbiguousOrTooShort)
    }

    @Test
    fun testCacheKeyIsolationAndClearing() = runBlocking {
        val messageId = "cache_test_msg_1"
        val text = "Hallo"

        val res1 = repository.getOrTranslateMessage(
            messageId = messageId,
            text = text,
            targetLanguage = "eng_Latn",
            explicitSourceLanguage = "deu_Latn"
        ) as TranslationResult.Success

        // Mock cached entity return for exact match
        val cachedEntity = TranslationEntity(
            messageId = messageId,
            targetLanguage = "eng_Latn",
            sourceLanguage = "deu_Latn",
            translatedText = res1.translatedText,
            modelVersion = "nllb-200-distilled-600m-int8-1.0.0"
        )
        whenever(mockDao.getTranslation(messageId, "eng_Latn", "deu_Latn", "nllb-200-distilled-600m-int8-1.0.0")).thenReturn(cachedEntity)

        val res2 = repository.getOrTranslateMessage(
            messageId = messageId,
            text = text,
            targetLanguage = "eng_Latn",
            explicitSourceLanguage = "deu_Latn"
        ) as TranslationResult.Success

        assertEquals("Cached result must match first result", res1.translatedText, res2.translatedText)
        verify(mockDao, times(2)).getTranslation(messageId, "eng_Latn", "deu_Latn", "nllb-200-distilled-600m-int8-1.0.0")

        // Clear all translations
        repository.clearAllTranslations()
        verify(mockDao).clearAllTranslations()
    }

    @Test
    fun testUiMappingAssignmentByMessageId() {
        val msgSelf = MessageEntity(
            id = "msg_self_1",
            conversationId = "conv_1",
            senderId = "user_me",
            senderDeviceId = "dev_1",
            encryptedContent = com.whisprtext.app.crypto.LocalEncryptor.encrypt("Hello from me"),
            createdAt = System.currentTimeMillis(),
            syncStatus = "sent"
        )
        val msgOther = MessageEntity(
            id = "msg_other_1",
            conversationId = "conv_1",
            senderId = "user_other",
            senderDeviceId = "dev_2",
            encryptedContent = com.whisprtext.app.crypto.LocalEncryptor.encrypt("Bonjour"),
            createdAt = System.currentTimeMillis(),
            syncStatus = "delivered"
        )

        val translationStates = mapOf(
            "msg_other_1" to MessageTranslationState.Translated(
                translatedText = "Hello",
                sourceLanguage = "fra_Latn",
                targetLanguage = "eng_Latn"
            )
        )

        val uiModels = ChatViewModel.transformMessagesToUiModels(
            messages = listOf(msgSelf, msgOther),
            currentUserId = "user_me",
            translationStates = translationStates
        )

        val modelSelf = uiModels.first { it.message.id == "msg_self_1" }
        val modelOther = uiModels.first { it.message.id == "msg_other_1" }

        assertTrue("Outgoing message isSelf must be true", modelSelf.isSelf)
        assertFalse("Incoming message isSelf must be false", modelOther.isSelf)

        assertEquals("Outgoing message translation state must be None for MVP", MessageTranslationState.None, modelSelf.translationState)
        assertTrue("Incoming message translation state must be Translated", modelOther.translationState is MessageTranslationState.Translated)
        assertEquals("Hello", (modelOther.translationState as MessageTranslationState.Translated).translatedText)
    }
}
