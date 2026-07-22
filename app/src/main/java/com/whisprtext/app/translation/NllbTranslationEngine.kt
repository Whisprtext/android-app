package com.whisprtext.app.translation

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

private class OnnxTensorHolder(
    val data: FloatArray,
    val shape: LongArray
) {
    fun toTensor(env: OrtEnvironment): OnnxTensor {
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)
    }
}

class NllbTranslationEngine(
    private val context: Context,
    private val modelDirSupplier: () -> File = {
        File(context.filesDir, "translation/nllb-200-distilled-600m-int8-1.0.0")
    }
) : TranslationEngine {

    companion object {
        private const val TAG = "NllbTranslationEngine"
        const val MODEL_VERSION = "nllb-200-distilled-600m-int8-1.0.0"
        const val MAX_NEW_TOKENS = 64
        const val EOS_TOKEN_ID = 2L
        const val PAD_TOKEN_ID = 1L
        const val UNK_TOKEN_ID = 3L
    }

    private val mutex = Mutex()

    @Volatile
    private var env: OrtEnvironment? = null

    @Volatile
    private var encoderSession: OrtSession? = null

    @Volatile
    private var decoderSession: OrtSession? = null

    @Volatile
    private var decoderWithPastSession: OrtSession? = null

    @Volatile
    private var tokenizer: NllbBpeTokenizer? = null

    private fun isLoaded(): Boolean =
        encoderSession != null && decoderSession != null && decoderWithPastSession != null

    private suspend fun ensureLoaded(): Result<Unit> = withContext(Dispatchers.IO) {
        if (isLoaded()) return@withContext Result.success(Unit)

        mutex.withLock {
            if (isLoaded()) return@withLock Result.success(Unit)

            val dir = modelDirSupplier()
            val encoderFile = File(dir, "encoder_model.onnx")
            val decoderFile = File(dir, "decoder_model.onnx")
            val decoderWithPastFile = File(dir, "decoder_with_past_model.onnx")
            val tokenizerFile = File(dir, "tokenizer.json")

            if (!encoderFile.exists() || !decoderFile.exists() || !decoderWithPastFile.exists()) {
                val err = "ONNX model files missing in ${dir.absolutePath}"
                logE(TAG, err)
                return@withLock Result.failure(IllegalStateException(err))
            }

            try {
                val ortEnv = OrtEnvironment.getEnvironment()
                val sessionOptions = OrtSession.SessionOptions().apply {
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                    setIntraOpNumThreads(2)
                }

                logI(TAG, "Loading ONNX sessions from ${dir.absolutePath}")
                encoderSession = ortEnv.createSession(encoderFile.absolutePath, sessionOptions)
                decoderSession = ortEnv.createSession(decoderFile.absolutePath, sessionOptions)
                decoderWithPastSession = ortEnv.createSession(decoderWithPastFile.absolutePath, sessionOptions)
                env = ortEnv

                if (tokenizerFile.exists()) {
                    tokenizer = NllbBpeTokenizer(tokenizerFile)
                    logI(TAG, "NllbBpeTokenizer loaded successfully")
                }

                Result.success(Unit)
            } catch (e: Exception) {
                logE(TAG, "Failed to load ONNX session: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    private val inferenceMutex = Mutex()

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): Result<String> = translate(text, sourceLanguage, targetLanguage, "")

    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        messageId: String
    ): Result<String> = inferenceMutex.withLock {
        translateInternal(text, sourceLanguage, targetLanguage, messageId)
    }

    private suspend fun translateInternal(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        messageId: String
    ): Result<String> = withContext(Dispatchers.Default) {
        val loadResult = ensureLoaded()
        if (loadResult.isFailure) {
            return@withContext Result.failure(loadResult.exceptionOrNull()!!)
        }

        val tok = tokenizer ?: return@withContext Result.failure(
            IllegalStateException("Tokenizer unavailable")
        )

        try {
            val encSession = encoderSession!!
            val decSession = decoderSession!!
            val decPastSession = decoderWithPastSession!!
            val ortEnv = env!!

            val sourceLangId = tok.getTokenId(sourceLanguage) ?: 256042L // deu_Latn fallback
            val targetLangId = tok.getTokenId(targetLanguage) ?: 256047L // eng_Latn fallback

            logI(TAG, "[TranslationStart]\nmessageId=$messageId\ninputText=$text\nsourceLanguage=$sourceLanguage\ntargetLanguage=$targetLanguage\ncacheHit=false")

            val inputTokenIds = tok.encode(text, sourceLangId)
            val seqLen = inputTokenIds.size.toLong()

            // 1. Encoder forward pass
            val inputIdsBuffer = LongBuffer.wrap(inputTokenIds)
            val attentionMaskBuffer = LongBuffer.wrap(LongArray(inputTokenIds.size) { 1L })

            val inputIdsTensor = OnnxTensor.createTensor(ortEnv, inputIdsBuffer, longArrayOf(1, seqLen))
            val attentionMaskTensor = OnnxTensor.createTensor(ortEnv, attentionMaskBuffer, longArrayOf(1, seqLen))

            val encoderInputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor
            )

            val encoderOutputs = encSession.run(encoderInputs)
            val lastHiddenState = encoderOutputs.get(0) as OnnxTensor

            logI(TAG, "[EncoderComplete]\nmessageId=$messageId\nencoderOutputShape=${lastHiddenState.info.shape.contentToString()}")

            // 2. Decoder Step 0 using decoder_model.onnx - Fresh state initialized per request
            val generatedTokens = mutableListOf<Long>()
            generatedTokens.add(EOS_TOKEN_ID) // 2L
            generatedTokens.add(targetLangId)

            val decInitialInputIds = longArrayOf(EOS_TOKEN_ID, targetLangId)
            val decInitialBuffer = LongBuffer.wrap(decInitialInputIds)
            val decInitialTensor = OnnxTensor.createTensor(ortEnv, decInitialBuffer, longArrayOf(1, 2))

            val decInitialInputs = mapOf(
                "input_ids" to decInitialTensor,
                "encoder_attention_mask" to attentionMaskTensor,
                "encoder_hidden_states" to lastHiddenState
            )

            val pastKeyValuesHolders = mutableMapOf<String, OnnxTensorHolder>()
            var nextTokenId = EOS_TOKEN_ID
            var vocabSize = 0

            logI(TAG, "[DecoderStart]\nmessageId=$messageId\ndecoderStartTokenId=$EOS_TOKEN_ID\ntargetLanguageTokenId=$targetLangId\npastCacheCreated=true")

            decSession.run(decInitialInputs).use { decInitialOutputs ->
                val logitsTensor0 = decInitialOutputs.get(0) as OnnxTensor
                val floatBuffer0 = logitsTensor0.floatBuffer
                vocabSize = (floatBuffer0.capacity() / 2).toInt()

                // Pick argmax at position 1 (last position of initial input)
                val offset0 = 1 * vocabSize
                var maxLogit = Float.NEGATIVE_INFINITY

                for (v in 0 until vocabSize) {
                    val logit = floatBuffer0.get(offset0 + v)
                    if (logit > maxLogit) {
                        maxLogit = logit
                        nextTokenId = v.toLong()
                    }
                }

                // Extract present key values for step 0
                val decOutputsNames = decSession.outputNames.toList()
                for (i in 1 until decInitialOutputs.size()) {
                    val origName = decOutputsNames[i]
                    val pastName = "past_key_values." + origName.removePrefix("present.")
                    val tensor = decInitialOutputs.get(i) as OnnxTensor
                    val shape = tensor.info.shape
                    val fb = tensor.floatBuffer
                    val data = FloatArray(fb.capacity())
                    fb.get(data)
                    pastKeyValuesHolders[pastName] = OnnxTensorHolder(data, shape)
                }
            }
            decInitialTensor.close()

            if (nextTokenId != EOS_TOKEN_ID) {
                generatedTokens.add(nextTokenId)

                // 3. Decoder Steps 1..N using decoder_with_past_model.onnx
                val maxSteps = minOf(MAX_NEW_TOKENS, (seqLen * 2 + 10).toInt())
                for (step in 1 until maxSteps) {
                    val stepInputIds = longArrayOf(nextTokenId)
                    val stepInputBuffer = LongBuffer.wrap(stepInputIds)
                    val stepInputTensor = OnnxTensor.createTensor(ortEnv, stepInputBuffer, longArrayOf(1, 1))

                    val pastTensors = pastKeyValuesHolders.mapValues { it.value.toTensor(ortEnv) }

                    val decPastInputs = mutableMapOf<String, OnnxTensor>()
                    decPastInputs["input_ids"] = stepInputTensor
                    decPastInputs["encoder_attention_mask"] = attentionMaskTensor
                    decPastInputs.putAll(pastTensors)

                    decPastSession.run(decPastInputs).use { decPastOutputs ->
                        val pastLogitsTensor = decPastOutputs.get(0) as OnnxTensor
                        val pastFloatBuffer = pastLogitsTensor.floatBuffer

                        logI(TAG, "[DecoderStep]\nmessageId=$messageId\nstep=$step\nselectedTokenId=$nextTokenId\nlogitsShape=${pastLogitsTensor.info.shape.contentToString()}\npastCacheReusedForSameMessage=true")

                        var maxLogit = Float.NEGATIVE_INFINITY
                        nextTokenId = EOS_TOKEN_ID

                        for (v in 0 until vocabSize) {
                            val logit = pastFloatBuffer.get(v)
                            if (logit > maxLogit) {
                                maxLogit = logit
                                nextTokenId = v.toLong()
                            }
                        }

                        val pastOutputNames = decPastSession.outputNames.toList()
                        for (i in 1 until decPastOutputs.size()) {
                            val origName = pastOutputNames[i]
                            val pastName = "past_key_values." + origName.removePrefix("present.")
                            val tensor = decPastOutputs.get(i) as OnnxTensor
                            val shape = tensor.info.shape
                            val fb = tensor.floatBuffer
                            val existingHolder = pastKeyValuesHolders[pastName]
                            if (existingHolder != null && existingHolder.data.size == fb.capacity()) {
                                fb.get(existingHolder.data)
                                pastKeyValuesHolders[pastName] = OnnxTensorHolder(existingHolder.data, shape)
                            } else {
                                val data = FloatArray(fb.capacity())
                                fb.get(data)
                                pastKeyValuesHolders[pastName] = OnnxTensorHolder(data, shape)
                            }
                        }
                    }

                    stepInputTensor.close()
                    pastTensors.values.forEach { it.close() }

                    if (nextTokenId == EOS_TOKEN_ID) {
                        break
                    }

                    generatedTokens.add(nextTokenId)
                }
            }

            inputIdsTensor.close()
            attentionMaskTensor.close()
            encoderOutputs.close()

            val rawDecoded = tok.decode(generatedTokens)
            val decodedText = postProcessTranslation(rawDecoded)

            logI(TAG, "[TranslationComplete]\nmessageId=$messageId\ndecodedOutput=$decodedText")

            logDebugInfo(
                messageId = messageId,
                rawInputText = text,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                sourceLangId = sourceLangId,
                targetLangId = targetLangId,
                inputTokenIds = inputTokenIds,
                generatedTokens = generatedTokens,
                decodedOutput = decodedText,
                fromCache = false
            )

            Result.success(decodedText)
        } catch (e: Exception) {
            logE(TAG, "Inference error during translate: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun logDebugInfo(
        messageId: String,
        rawInputText: String,
        sourceLanguage: String,
        targetLanguage: String,
        sourceLangId: Long,
        targetLangId: Long,
        inputTokenIds: LongArray,
        generatedTokens: List<Long>,
        decodedOutput: String,
        fromCache: Boolean
    ) {
        val first10Gen = generatedTokens.take(10)
        val msg = """
            |=== DEBUG TRANSLATION INSTRUMENTATION ===
            |Message ID: $messageId
            |Raw Input Text: $rawInputText
            |Source Language: $sourceLanguage (Token ID: $sourceLangId)
            |Target Language: $targetLanguage (Token ID: $targetLangId)
            |Input Token IDs: ${inputTokenIds.joinToString(", ", "[", "]")}
            |Decoder Start Token ID: $EOS_TOKEN_ID
            |First 10 Generated Token IDs: ${first10Gen.joinToString(", ", "[", "]")}
            |EOS Token ID: $EOS_TOKEN_ID
            |Final Decoded Output: $decodedOutput
            |From Cache: $fromCache
            |Model Version: $MODEL_VERSION
            |=========================================
        """.trimMargin()
        logI(TAG, msg)
    }

    private fun postProcessTranslation(text: String): String {
        var cleaned = text.trim()
        cleaned = cleaned.replace("\\s+([.,!?:;])".toRegex(), "$1")
        return cleaned.trim()
    }

    private fun logE(tag: String, msg: String, tr: Throwable? = null) {
        try { Log.e(tag, msg, tr) } catch (_: Throwable) { println("ERROR: [$tag] $msg") }
    }
    private fun logI(tag: String, msg: String) {
        try { Log.i(tag, msg) } catch (_: Throwable) { println("INFO: [$tag] $msg") }
    }
}
