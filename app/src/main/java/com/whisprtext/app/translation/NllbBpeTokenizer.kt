package com.whisprtext.app.translation

import com.google.gson.stream.JsonReader
import java.io.File
import java.io.InputStreamReader

class NllbBpeTokenizer(tokenizerFile: File) {

    private val vocab = mutableMapOf<String, Long>()
    private val idToToken = mutableMapOf<Long, String>()
    private val rankMap = mutableMapOf<Pair<String, String>, Int>()

    init {
        loadTokenizer(tokenizerFile)
    }

    private fun loadTokenizer(tokenizerFile: File) {
        if (!tokenizerFile.exists()) return

        InputStreamReader(tokenizerFile.inputStream(), Charsets.UTF_8).use { isr ->
            val reader = JsonReader(isr)
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                when (name) {
                    "added_tokens" -> parseAddedTokens(reader)
                    "model" -> parseModel(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
    }

    private fun parseAddedTokens(reader: JsonReader) {
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var content: String? = null
            var id: Long? = null
            while (reader.hasNext()) {
                val key = reader.nextName()
                if (key == "content") {
                    content = reader.nextString()
                } else if (key == "id") {
                    id = reader.nextLong()
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()

            if (content != null && id != null) {
                vocab[content] = id
                idToToken[id] = content
            }
        }
        reader.endArray()
    }

    private fun parseModel(reader: JsonReader) {
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            when (key) {
                "vocab" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val token = reader.nextName()
                        val id = reader.nextLong()
                        vocab[token] = id
                        idToToken[id] = token
                    }
                    reader.endObject()
                }
                "merges" -> {
                    reader.beginArray()
                    var rank = 0
                    while (reader.hasNext()) {
                        val token1: String
                        val token2: String
                        if (reader.peek() == com.google.gson.stream.JsonToken.BEGIN_ARRAY) {
                            reader.beginArray()
                            token1 = reader.nextString()
                            token2 = reader.nextString()
                            reader.endArray()
                        } else {
                            val mergeStr = reader.nextString()
                            val parts = mergeStr.split(" ")
                            token1 = parts.getOrElse(0) { "" }
                            token2 = parts.getOrElse(1) { "" }
                        }
                        if (token1.isNotEmpty() && token2.isNotEmpty()) {
                            rankMap[Pair(token1, token2)] = rank
                        }
                        rank++
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    fun getTokenId(token: String): Long? {
        return vocab[token] ?: vocab["\u2581$token"] ?: vocab[" $token"]
    }

    private fun getPairs(word: List<String>): Set<Pair<String, String>> {
        val pairs = mutableSetOf<Pair<String, String>>()
        for (i in 0 until word.size - 1) {
            pairs.add(Pair(word[i], word[i + 1]))
        }
        return pairs
    }

    private fun bpeTokenizeWord(word: String): List<String> {
        var symbols = word.map { it.toString() }
        while (symbols.size > 1) {
            val pairs = getPairs(symbols)
            var minRank = Int.MAX_VALUE
            var minPair: Pair<String, String>? = null

            for (p in pairs) {
                val r = rankMap[p]
                if (r != null && r < minRank) {
                    minRank = r
                    minPair = p
                }
            }

            if (minPair == null) break

            val first = minPair.first
            val second = minPair.second
            val newSymbols = mutableListOf<String>()
            var i = 0
            while (i < symbols.size) {
                if (i < symbols.size - 1 && symbols[i] == first && symbols[i + 1] == second) {
                    newSymbols.add(first + second)
                    i += 2
                } else {
                    newSymbols.add(symbols[i])
                    i++
                }
            }
            symbols = newSymbols
        }
        return symbols
    }

    private fun normalizeText(text: String): String {
        val nfkc = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
        return nfkc
            .replace("，", ",")
            .replace("？", "?")
            .replace("！", "!")
            .replace("：", ":")
            .replace("；", ";")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("‘", "'")
            .replace("’", "'")
    }

    fun encode(text: String, sourceLangId: Long): LongArray {
        val tokenIds = mutableListOf<Long>()
        tokenIds.add(sourceLangId) // Source language token prepended first

        val normalizedInput = normalizeText(text)
        val metaspaceText = if (normalizedInput.startsWith("\u2581")) {
            normalizedInput.replace(" ", "\u2581")
        } else {
            "\u2581" + normalizedInput.replace(" ", "\u2581")
        }

        val subwords = bpeTokenizeWord(metaspaceText)
        val unkId = vocab["<unk>"] ?: 3L

        for (sub in subwords) {
            val id = vocab[sub]
            if (id != null) {
                tokenIds.add(id)
            } else {
                for (ch in sub) {
                    val chId = vocab[ch.toString()] ?: unkId
                    tokenIds.add(chId)
                }
            }
        }

        tokenIds.add(2L) // </s> EOS token appended last
        return tokenIds.toLongArray()
    }

    fun decode(tokenIds: List<Long>): String {
        val sb = StringBuilder()
        for (id in tokenIds) {
            if (id <= 3L || id >= 256000L) continue // Skip special tokens & language codes
            val tokStr = idToToken[id] ?: continue
            sb.append(tokStr)
        }
        val rawText = sb.toString()
        val cleanedText = rawText.replace("\u2581", " ").trim()
        return cleanedText
    }
}
