package com.whisprtext.app.translation

data class LanguageDetectionResult(
    val languageCode: String?,
    val confidence: Float,
    val isSupported: Boolean,
    val isAmbiguousOrTooShort: Boolean,
)

interface LanguageDetector {
    fun detectLanguage(text: String): LanguageDetectionResult
}

class LocalLanguageDetector : LanguageDetector {

    companion object {
        private val SHORT_AMBIGUOUS_WORDS = setOf(
            "ok", "okay", "lol", "lmao", "rofl", "hi", "hey", "yes", "no", "k", "thx", "thanks", "bye"
        )

        private fun isOnlyEmojiOrPunctuation(text: String): Boolean {
            for (ch in text) {
                if (Character.isLetterOrDigit(ch)) {
                    return false
                }
            }
            return true
        }
    }

    override fun detectLanguage(text: String): LanguageDetectionResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return LanguageDetectionResult(
                languageCode = null,
                confidence = 0.0f,
                isSupported = false,
                isAmbiguousOrTooShort = true,
            )
        }

        // Check if text contains no letters or digits (only punctuation, emojis, whitespace, or symbols)
        if (isOnlyEmojiOrPunctuation(trimmed)) {
            return LanguageDetectionResult(
                languageCode = null,
                confidence = 0.0f,
                isSupported = false,
                isAmbiguousOrTooShort = true,
            )
        }

        // Check short ambiguous words like ok, lol, thx
        if (SHORT_AMBIGUOUS_WORDS.contains(trimmed.lowercase())) {
            return LanguageDetectionResult(
                languageCode = null,
                confidence = 0.2f,
                isSupported = false,
                isAmbiguousOrTooShort = true,
            )
        }

        // Script counts across 200 NLLB supported scripts
        var devanagariCount = 0
        var hiraganaKatakanaCount = 0
        var hanziCount = 0
        var cyrillicCount = 0
        var arabicCount = 0
        var bengaliCount = 0
        var tamilCount = 0
        var teluguCount = 0
        var kannadaCount = 0
        var malayalamCount = 0
        var gujaratiCount = 0
        var gurmukhiCount = 0
        var thaiCount = 0
        var koreanCount = 0
        var hebrewCount = 0
        var greekCount = 0
        var georgianCount = 0
        var armenianCount = 0
        var khmerCount = 0
        var laoCount = 0
        var sinhalaCount = 0
        var latinCount = 0

        for (ch in trimmed) {
            val block = Character.UnicodeBlock.of(ch)
            when (block) {
                Character.UnicodeBlock.DEVANAGARI -> devanagariCount++
                Character.UnicodeBlock.HIRAGANA, Character.UnicodeBlock.KATAKANA -> hiraganaKatakanaCount++
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS -> hanziCount++
                Character.UnicodeBlock.CYRILLIC -> cyrillicCount++
                Character.UnicodeBlock.ARABIC -> arabicCount++
                Character.UnicodeBlock.BENGALI -> bengaliCount++
                Character.UnicodeBlock.TAMIL -> tamilCount++
                Character.UnicodeBlock.TELUGU -> teluguCount++
                Character.UnicodeBlock.KANNADA -> kannadaCount++
                Character.UnicodeBlock.MALAYALAM -> malayalamCount++
                Character.UnicodeBlock.GUJARATI -> gujaratiCount++
                Character.UnicodeBlock.GURMUKHI -> gurmukhiCount++
                Character.UnicodeBlock.THAI -> thaiCount++
                Character.UnicodeBlock.HANGUL_SYLLABLES, Character.UnicodeBlock.HANGUL_JAMO -> koreanCount++
                Character.UnicodeBlock.HEBREW -> hebrewCount++
                Character.UnicodeBlock.GREEK -> greekCount++
                Character.UnicodeBlock.GEORGIAN -> georgianCount++
                Character.UnicodeBlock.ARMENIAN -> armenianCount++
                Character.UnicodeBlock.KHMER -> khmerCount++
                Character.UnicodeBlock.LAO -> laoCount++
                Character.UnicodeBlock.SINHALA -> sinhalaCount++
                Character.UnicodeBlock.BASIC_LATIN,
                Character.UnicodeBlock.LATIN_1_SUPPLEMENT,
                Character.UnicodeBlock.LATIN_EXTENDED_A -> latinCount++
                else -> {
                    if (Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.HAN) {
                        hanziCount++
                    }
                }
            }
        }

        val totalLetters = devanagariCount + hiraganaKatakanaCount + hanziCount + cyrillicCount +
                arabicCount + bengaliCount + tamilCount + teluguCount + kannadaCount +
                malayalamCount + gujaratiCount + gurmukhiCount + thaiCount + koreanCount +
                hebrewCount + greekCount + georgianCount + armenianCount + khmerCount +
                laoCount + sinhalaCount + latinCount

        if (totalLetters == 0) {
            return LanguageDetectionResult(null, 0.0f, false, true)
        }

        return when {
            devanagariCount > totalLetters * 0.3 -> LanguageDetectionResult("hin_Deva", 0.95f, true, false)
            hiraganaKatakanaCount > totalLetters * 0.2 -> LanguageDetectionResult("jpn_Jpan", 0.95f, true, false)
            hanziCount > totalLetters * 0.3 -> LanguageDetectionResult("zho_Hans", 0.95f, true, false)
            cyrillicCount > totalLetters * 0.3 -> LanguageDetectionResult("rus_Cyrl", 0.95f, true, false)
            arabicCount > totalLetters * 0.3 -> LanguageDetectionResult("arb_Arab", 0.95f, true, false)
            bengaliCount > totalLetters * 0.3 -> LanguageDetectionResult("ben_Beng", 0.95f, true, false)
            tamilCount > totalLetters * 0.3 -> LanguageDetectionResult("tam_Taml", 0.95f, true, false)
            teluguCount > totalLetters * 0.3 -> LanguageDetectionResult("tel_Telu", 0.95f, true, false)
            kannadaCount > totalLetters * 0.3 -> LanguageDetectionResult("kan_Knda", 0.95f, true, false)
            malayalamCount > totalLetters * 0.3 -> LanguageDetectionResult("mal_Mlym", 0.95f, true, false)
            gujaratiCount > totalLetters * 0.3 -> LanguageDetectionResult("guj_Gujr", 0.95f, true, false)
            gurmukhiCount > totalLetters * 0.3 -> LanguageDetectionResult("pan_Guru", 0.95f, true, false)
            thaiCount > totalLetters * 0.3 -> LanguageDetectionResult("tha_Thai", 0.95f, true, false)
            koreanCount > totalLetters * 0.3 -> LanguageDetectionResult("kor_Hang", 0.95f, true, false)
            hebrewCount > totalLetters * 0.3 -> LanguageDetectionResult("heb_Hebr", 0.95f, true, false)
            greekCount > totalLetters * 0.3 -> LanguageDetectionResult("ell_Grek", 0.95f, true, false)
            georgianCount > totalLetters * 0.3 -> LanguageDetectionResult("kat_Geor", 0.95f, true, false)
            armenianCount > totalLetters * 0.3 -> LanguageDetectionResult("hye_Armn", 0.95f, true, false)
            khmerCount > totalLetters * 0.3 -> LanguageDetectionResult("khm_Khmr", 0.95f, true, false)
            laoCount > totalLetters * 0.3 -> LanguageDetectionResult("lao_Laoo", 0.95f, true, false)
            sinhalaCount > totalLetters * 0.3 -> LanguageDetectionResult("sin_Sinh", 0.95f, true, false)
            latinCount > totalLetters * 0.3 -> {
                detectLatinSubLanguage(trimmed)
            }
            else -> LanguageDetectionResult("eng_Latn", 0.4f, true, true)
        }
    }

    private fun detectLatinSubLanguage(text: String): LanguageDetectionResult {
        val lowerText = text.lowercase()
        val words = lowerText.split("\\s+|[.,!?:;\"'()]+".toRegex()).filter { it.isNotEmpty() }.toSet()

        var vietnameseScore = 0
        var frenchScore = 0
        var germanScore = 0
        var spanishScore = 0
        var portugueseScore = 0
        var italianScore = 0
        var dutchScore = 0
        var swedishScore = 0
        var polishScore = 0
        var turkishScore = 0
        var indonesianScore = 0

        // Check Vietnamese unique characters/diacritics
        val vietnameseChars = listOf("đ", "Đ", "ơ", "ư", "ă", "â", "ê", "ô", "̀", "́", "̉", "̃", "̣")
        for (c in vietnameseChars) {
            if (text.contains(c)) {
                vietnameseScore += 3
            }
        }

        // Distinctive word dictionaries
        val vietnameseWords = setOf("chúc", "một", "ngày", "tốt", "lành", "xin", "chào", "cảm", "ơn", "bạn", "khỏe", "không", "tôi", "anh", "em", "với", "như", "được")
        val frenchWords = setOf("bonjour", "salut", "comment", "allez", "vous", "fais", "quoi", "en", "ce", "moment", "avez", "merci", "revoir", "plaît", "avec", "très")
        val germanWords = setOf("hallo", "guten", "morgen", "tag", "abend", "nacht", "danke", "bitte", "wie", "geht", "dir", "nicht", "wiedersehen", "schön", "deutsch")
        val spanishWords = setOf("hola", "cómo", "estás", "está", "gracias", "buenos", "días", "adiós", "señor", "tarde", "noche", "favor", "muchas", "amigo")
        val portugueseWords = setOf("olá", "obrigado", "obrigada", "tudo", "bem", "boa", "você")
        val italianWords = setOf("ciao", "grazie", "buongiorno", "buonasera", "come", "stai", "molto", "bene", "prego", "favore")
        val dutchWords = setOf("goedemorgen", "goedemiddag", "hoe", "gaat", "met", "alsjeblieft")
        val swedishWords = setOf("hej", "tack", "mycket", "morgon", "mår")
        val polishWords = setOf("cześć", "dziękuję", "dzień", "dobry", "masz")
        val turkishWords = setOf("merhaba", "teşekkürler", "günaydın", "nasılsın", "evet", "hayır")
        val indonesianWords = setOf("halo", "terima", "kasih", "selamat", "pagi", "siang", "malam", "kabar")

        for (w in words) {
            if (vietnameseWords.contains(w)) vietnameseScore += 4
            if (frenchWords.contains(w)) frenchScore += 4
            if (germanWords.contains(w)) germanScore += 4
            if (spanishWords.contains(w)) spanishScore += 4
            if (portugueseWords.contains(w)) portugueseScore += 4
            if (italianWords.contains(w)) italianScore += 4
            if (dutchWords.contains(w)) dutchScore += 4
            if (swedishWords.contains(w)) swedishScore += 4
            if (polishWords.contains(w)) polishScore += 4
            if (turkishWords.contains(w)) turkishScore += 4
            if (indonesianWords.contains(w)) indonesianScore += 4
        }

        // Distinctive diacritic characters per language
        if (text.contains("¿") || text.contains("¡") || text.contains("ñ")) spanishScore += 4
        if (text.contains("ç") || text.contains("œ") || text.contains("æ")) frenchScore += 3
        if (text.contains("ß") || text.contains("ä") || text.contains("ö") || text.contains("ü")) germanScore += 3
        if (text.contains("ã") || text.contains("õ")) portugueseScore += 3
        if (text.contains("ą") || text.contains("ę") || text.contains("ż") || text.contains("ź") || text.contains("ł") || text.contains("ń")) polishScore += 3
        if (text.contains("ğ") || text.contains("ı") || text.contains("ş")) turkishScore += 3

        val scores = listOf(
            "vie_Latn" to vietnameseScore,
            "fra_Latn" to frenchScore,
            "deu_Latn" to germanScore,
            "spa_Latn" to spanishScore,
            "por_Latn" to portugueseScore,
            "ita_Latn" to italianScore,
            "nld_Latn" to dutchScore,
            "swe_Latn" to swedishScore,
            "pol_Latn" to polishScore,
            "tur_Latn" to turkishScore,
            "ind_Latn" to indonesianScore
        ).sortedByDescending { it.second }

        val topScore = scores.first()
        val secondScore = scores[1]

        if (topScore.second >= 2 && topScore.second > secondScore.second) {
            val confidence = (0.75f + (topScore.second * 0.05f)).coerceAtMost(0.98f)
            return LanguageDetectionResult(
                languageCode = topScore.first,
                confidence = confidence,
                isSupported = true,
                isAmbiguousOrTooShort = false
            )
        }

        // Low-confidence or ambiguous Latin text
        return LanguageDetectionResult(
            languageCode = "eng_Latn",
            confidence = 0.40f,
            isSupported = true,
            isAmbiguousOrTooShort = true
        )
    }
}
