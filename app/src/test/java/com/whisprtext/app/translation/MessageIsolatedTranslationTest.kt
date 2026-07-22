package com.whisprtext.app.translation

import android.content.Context
import com.whisprtext.app.data.local.dao.TranslationDao
import com.whisprtext.app.data.local.entity.TranslationEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.io.File

class MessageIsolatedTranslationTest {

    private lateinit var modelDir: File
    private lateinit var mockContext: Context
    private lateinit var realEngine: NllbTranslationEngine
    private lateinit var mockDao: TranslationDao
    private lateinit var repository: TranslationRepository

    @Before
    fun setup() {
        val rootAppDir = File("").absoluteFile.parentFile?.parentFile ?: File("C:/Users/nilan/Projects/whisprtext-app")
        modelDir = File(rootAppDir, "translation/models/nllb-int8")

        mockContext = mock()
        mockDao = mock()
        realEngine = NllbTranslationEngine(mockContext) { modelDir }

        repository = TranslationRepository(
            translationEngine = realEngine,
            translationDao = mockDao,
            languageDetector = LocalLanguageDetector(),
            modelVersion = "nllb-200-distilled-600m-int8-1.0.0"
        )
    }

    @Test
    fun test1_GermanToEnglishLiteralTranslation() = runBlocking {
        val input = "So hübsch, omg."
        val result = repository.getOrTranslateMessage(
            messageId = "msg_de_1",
            text = input,
            targetLanguage = "eng_Latn",
            explicitSourceLanguage = "deu_Latn"
        )

        assertTrue("Expected Success result", result is TranslationResult.Success)
        val success = result as TranslationResult.Success
        val output = success.translatedText

        println("Test 1 Output: $output")
        assertTrue("Output should convey pretty/prettyness", output.contains("pretty", ignoreCase = true))
        assertFalse("Output must reject unrelated meaning (e.g. fan)", output.contains("fan", ignoreCase = true))
    }

    @Test
    fun test2_EnglishToSpanishTranslation() = runBlocking {
        val input = "How are you today?"
        val result = repository.getOrTranslateMessage(
            messageId = "msg_es_1",
            text = input,
            targetLanguage = "spa_Latn",
            explicitSourceLanguage = "eng_Latn"
        )

        assertTrue("Expected Success result", result is TranslationResult.Success)
        val success = result as TranslationResult.Success
        val output = success.translatedText

        println("Test 2 Output: $output")
        assertTrue("Output should contain Spanish translation for 'How are you'", output.contains("Cómo", ignoreCase = true) || output.contains("estás", ignoreCase = true))
    }

    @Test
    fun test3_SequentialMessagesStateIsolation() = runBlocking {
        // Message A
        val resA = repository.getOrTranslateMessage(
            messageId = "msg_seq_A",
            text = "I like this.",
            targetLanguage = "eng_Latn",
            explicitSourceLanguage = "eng_Latn"
        )
        assertTrue(resA is TranslationResult.Success)

        // Message B
        val resB = repository.getOrTranslateMessage(
            messageId = "msg_seq_B",
            text = "So hübsch, omg.",
            targetLanguage = "eng_Latn",
            explicitSourceLanguage = "deu_Latn"
        )
        assertTrue(resB is TranslationResult.Success)
        val outputB = (resB as TranslationResult.Success).translatedText

        println("Test 3 Message B Output: $outputB")
        assertTrue("Message B result must contain pretty", outputB.contains("pretty", ignoreCase = true))
        assertFalse("Message B must not be influenced by Message A text", outputB.contains("like", ignoreCase = true))
    }

    @Test
    fun test4_DeterministicOutput() = runBlocking {
        val input = "How are you today?"
        val res1 = repository.getOrTranslateMessage(
            messageId = "msg_det_1",
            text = input,
            targetLanguage = "spa_Latn",
            explicitSourceLanguage = "eng_Latn"
        )
        val res2 = repository.getOrTranslateMessage(
            messageId = "msg_det_2",
            text = input,
            targetLanguage = "spa_Latn",
            explicitSourceLanguage = "eng_Latn"
        )

        assertTrue(res1 is TranslationResult.Success)
        assertTrue(res2 is TranslationResult.Success)
        val out1 = (res1 as TranslationResult.Success).translatedText
        val out2 = (res2 as TranslationResult.Success).translatedText

        assertEquals("Translation output must be strictly deterministic across runs", out1, out2)
    }

    @Test
    fun test5_SameSourceAndTargetSkipsInference() = runBlocking {
        val input = "Hello world!"
        val result = repository.getOrTranslateMessage(
            messageId = "msg_same_lang",
            text = input,
            targetLanguage = "eng_Latn",
            explicitSourceLanguage = "eng_Latn"
        )

        assertTrue(result is TranslationResult.Success)
        val success = result as TranslationResult.Success
        assertEquals("Identical source and target must return original text unchanged", input, success.translatedText)
    }

    @Test
    fun test6_EmptyOrWhitespaceMessageSkipsInference() = runBlocking {
        val result = repository.getOrTranslateMessage(
            messageId = "msg_empty",
            text = "   \n\t  ",
            targetLanguage = "eng_Latn"
        )

        assertTrue("Empty text must be skipped", result is TranslationResult.Skipped)
    }

    @Test
    fun test7_EmojiOnlyMessagePreservedOrSkipped() = runBlocking {
        val emojiInput = "😊👍🎉"
        val result = repository.getOrTranslateMessage(
            messageId = "msg_emoji",
            text = emojiInput,
            targetLanguage = "spa_Latn",
            explicitSourceLanguage = "eng_Latn"
        )

        assertTrue("Emoji-only message should succeed without crashing", result is TranslationResult.Success)
        val success = result as TranslationResult.Success
        assertEquals("Emoji-only text should be preserved", emojiInput, success.translatedText)
    }

    @Test
    fun test8_PreserveSpecialContent() = runBlocking {
        val inputWithSpecial = "Check out https://whisprtext.com from @john for 50 dollars!"
        val result = repository.getOrTranslateMessage(
            messageId = "msg_special",
            text = inputWithSpecial,
            targetLanguage = "spa_Latn",
            explicitSourceLanguage = "eng_Latn"
        )

        assertTrue(result is TranslationResult.Success)
        val output = (result as TranslationResult.Success).translatedText

        assertTrue("Output should preserve URL", output.contains("https://whisprtext.com"))
        assertTrue("Output should preserve username mention", output.contains("@john"))
    }

    @Test
    fun testThreeMessageSequentialRegression() = runBlocking {
        // Message A: hallo (deu_Latn -> eng_Latn)
        val resA = repository.getOrTranslateMessage(
            messageId = "message-a",
            text = "hallo",
            targetLanguage = "eng_Latn",
            explicitSourceLanguage = "deu_Latn"
        )
        assertTrue("Message A must succeed", resA is TranslationResult.Success)
        val outA = (resA as TranslationResult.Success).translatedText
        println("Message A output: $outA")
        assertEquals("message-a", resA.messageId)
        assertTrue("Message A should be Hello/Hi", outA.contains("hello", ignoreCase = true) || outA.contains("hi", ignoreCase = true))

        // Message B: こんにちは (jpn_Jpan -> eng_Latn)
        val resB = repository.getOrTranslateMessage(
            messageId = "message-b",
            text = "こんにちは",
            targetLanguage = "eng_Latn",
            explicitSourceLanguage = "jpn_Jpan"
        )
        assertTrue("Message B must succeed", resB is TranslationResult.Success)
        val outB = (resB as TranslationResult.Success).translatedText
        println("Message B output: $outB")
        assertEquals("message-b", resB.messageId)
        assertFalse("Message B must not contain output from Message A", outB.equals(outA, ignoreCase = true))
        assertTrue("Message B should be Hello/Good afternoon", outB.contains("hello", ignoreCase = true) || outB.contains("good afternoon", ignoreCase = true) || outB.contains("hi", ignoreCase = true))

        // Message C: 很好，你呢？ (zho_Hans -> eng_Latn)
        val resC = repository.getOrTranslateMessage(
            messageId = "message-c",
            text = "很好，你呢？",
            targetLanguage = "eng_Latn",
            explicitSourceLanguage = "zho_Hans"
        )
        assertTrue("Message C must succeed", resC is TranslationResult.Success)
        val outC = (resC as TranslationResult.Success).translatedText
        println("Message C output: $outC")
        assertEquals("message-c", resC.messageId)
        assertFalse("Message C must not contain output from Message B", outC.equals(outB, ignoreCase = true))
        assertTrue("Message C should convey good/how about you", outC.contains("good", ignoreCase = true) || outC.contains("you", ignoreCase = true))
    }

    @Test
    fun testThreeMessageConcurrentRequests() = runBlocking {
        val defA = async {
            repository.getOrTranslateMessage(
                messageId = "message-a-concurrent",
                text = "hallo",
                targetLanguage = "eng_Latn",
                explicitSourceLanguage = "deu_Latn"
            )
        }
        val defB = async {
            repository.getOrTranslateMessage(
                messageId = "message-b-concurrent",
                text = "こんにちは",
                targetLanguage = "eng_Latn",
                explicitSourceLanguage = "jpn_Jpan"
            )
        }
        val defC = async {
            repository.getOrTranslateMessage(
                messageId = "message-c-concurrent",
                text = "很好，你呢？",
                targetLanguage = "eng_Latn",
                explicitSourceLanguage = "zho_Hans"
            )
        }

        val resA = defA.await() as TranslationResult.Success
        val resB = defB.await() as TranslationResult.Success
        val resC = defC.await() as TranslationResult.Success

        assertEquals("message-a-concurrent", resA.messageId)
        assertEquals("message-b-concurrent", resB.messageId)
        assertEquals("message-c-concurrent", resC.messageId)

        assertTrue("Concurrent A should be Hello/Hi", resA.translatedText.contains("hello", ignoreCase = true) || resA.translatedText.contains("hi", ignoreCase = true))
        assertTrue("Concurrent B should be Hello/Good afternoon", resB.translatedText.contains("hello", ignoreCase = true) || resB.translatedText.contains("good afternoon", ignoreCase = true) || resB.translatedText.contains("hi", ignoreCase = true))
        assertTrue("Concurrent C should convey good/you", resC.translatedText.contains("good", ignoreCase = true) || resC.translatedText.contains("you", ignoreCase = true))

        assertNotEquals("A and B must be distinct", resA.translatedText, resB.translatedText)
        assertNotEquals("B and C must be distinct", resB.translatedText, resC.translatedText)
    }

    @Test
    fun testRepeatedRequestCacheKey() = runBlocking {
        val messageId = "message-repeat-1"
        val text = "hallo"
        val target = "eng_Latn"
        val source = "deu_Latn"

        val res1 = repository.getOrTranslateMessage(
            messageId = messageId,
            text = text,
            targetLanguage = target,
            explicitSourceLanguage = source
        ) as TranslationResult.Success

        // Mock DAO returning cached entity for second request
        val cachedEntity = TranslationEntity(
            messageId = messageId,
            targetLanguage = target,
            sourceLanguage = source,
            translatedText = res1.translatedText,
            modelVersion = "nllb-200-distilled-600m-int8-1.0.0"
        )
        whenever(mockDao.getTranslation(messageId, target, source, "nllb-200-distilled-600m-int8-1.0.0")).thenReturn(cachedEntity)

        val res2 = repository.getOrTranslateMessage(
            messageId = messageId,
            text = text,
            targetLanguage = target,
            explicitSourceLanguage = source
        ) as TranslationResult.Success

        assertEquals(res1.translatedText, res2.translatedText)
        assertEquals(messageId, res2.messageId)
        assertEquals(source, res2.sourceLanguage)
        assertEquals(target, res2.targetLanguage)
    }
}
