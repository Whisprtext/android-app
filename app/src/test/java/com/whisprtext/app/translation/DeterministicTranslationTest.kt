package com.whisprtext.app.translation

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.File

class DeterministicTranslationTest {

    @Test
    fun testDeterministicTranslationDiagnostic() {
        val rootAppDir = File("").absoluteFile.parentFile?.parentFile ?: File("C:/Users/nilan/Projects/whisprtext-app")
        val modelDir = File(rootAppDir, "translation/models/nllb-int8")

        println("=== Deterministic Translation Diagnostic Test ===")
        println("Model Dir: ${modelDir.absolutePath}")

        val mockContext = mock<Context>()
        val engine = NllbTranslationEngine(mockContext) { modelDir }
        val detector = LocalLanguageDetector()

        val input = "So hübsch, omg."
        val explicitSrc = "deu_Latn"
        val explicitTgt = "eng_Latn"

        val detectionResult = detector.detectLanguage(input)
        println("1. Original input: $input")
        println("2. Detected source language: ${detectionResult.languageCode} (confidence: ${detectionResult.confidence})")
        println("3. Explicit source language: $explicitSrc")
        println("4. Target language: $explicitTgt")

        val tokenizerFile = File(modelDir, "tokenizer.json")
        val tokenizer = NllbBpeTokenizer(tokenizerFile)

        val srcLangTokenId = tokenizer.getTokenId(explicitSrc)
        val tgtLangTokenId = tokenizer.getTokenId(explicitTgt)

        println("5. Source language token ID: $srcLangTokenId")
        println("6. Target language token ID: $tgtLangTokenId")

        val tokenizedInputIds = if (srcLangTokenId != null) tokenizer.encode(input, srcLangTokenId) else longArrayOf()
        println("7. Tokenized input IDs: ${tokenizedInputIds.joinToString(", ", "[", "]")}")
        println("8. Decoder start token ID: ${NllbTranslationEngine.EOS_TOKEN_ID}")
        println("10. EOS token ID: ${NllbTranslationEngine.EOS_TOKEN_ID}")

        val encoderFile = File(modelDir, "encoder_model.onnx")
        val decoderFile = File(modelDir, "decoder_model.onnx")
        val decoderWithPastFile = File(modelDir, "decoder_with_past_model.onnx")

        println("12. ONNX model file paths:")
        println("    - Encoder: ${encoderFile.absolutePath} (exists: ${encoderFile.exists()})")
        println("    - Decoder: ${decoderFile.absolutePath} (exists: ${decoderFile.exists()})")
        println("    - Decoder with Past: ${decoderWithPastFile.absolutePath} (exists: ${decoderWithPastFile.exists()})")
        println("13. ONNX Runtime execution provider: CPUExecutionProvider")

        val runResult = kotlinx.coroutines.runBlocking {
            engine.translate(input, explicitSrc, explicitTgt)
        }

        val decodedOutput = runResult.getOrNull() ?: "FAILED: ${runResult.exceptionOrNull()?.message}"
        println("11. Final decoded output: $decodedOutput")

        assertNotNull("Translation engine returned failure: ${runResult.exceptionOrNull()}", runResult.getOrNull())
        org.junit.Assert.assertTrue("Output should convey pretty/prettyness", decodedOutput.contains("pretty", ignoreCase = true))
        org.junit.Assert.assertFalse("Output must reject unrelated meaning", decodedOutput.contains("fan", ignoreCase = true))
    }
}
