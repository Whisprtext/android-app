package com.whisprtext.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

import android.util.LruCache

object MarkdownParser {
    private val parseCache = LruCache<String, AnnotatedString>(500)

    private val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
    private val italicStyle = SpanStyle(fontStyle = FontStyle.Italic)
    private val strikeStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)
    private val codeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = Color.LightGray.copy(alpha = 0.3f)
    )
    private val markerStyle = SpanStyle(color = Color.Gray.copy(alpha = 0.5f))

    private val bulletRegex = Regex("""^(\s*)([-*•])\s+(.+)$""")
    private val numberedRegex = Regex("""^(\s*)(\d+)\.\s+(.+)$""")
    private val romanRegex = Regex("""^(\s*)([ivxldcmIVXLDCM]+)\.\s+(.+)$""")

    fun parse(text: String, hideMarkers: Boolean): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")
        val cacheKey = if (hideMarkers) "h_$text" else "s_$text"
        val cached = parseCache.get(cacheKey)
        if (cached != null) return cached

        val result = buildAnnotatedString {
            val lines = text.split('\n')
            lines.forEachIndexed { index, line ->
                if (index > 0) {
                    append('\n')
                }

                val bulletMatch = bulletRegex.matchEntire(line)
                val numberedMatch = numberedRegex.matchEntire(line)
                val romanMatch = romanRegex.matchEntire(line)

                when {
                    bulletMatch != null -> {
                        val indent = bulletMatch.groupValues[1]
                        val marker = bulletMatch.groupValues[2]
                        val content = bulletMatch.groupValues[3]
                        
                        append(indent)
                        if (hideMarkers) {
                            pushStyle(boldStyle)
                            append("•  ")
                            pop()
                        } else {
                            pushStyle(markerStyle)
                            append("$marker ")
                            pop()
                        }
                        parseInternal(content, hideMarkers, emptyList())
                    }
                    numberedMatch != null -> {
                        val indent = numberedMatch.groupValues[1]
                        val num = numberedMatch.groupValues[2]
                        val content = numberedMatch.groupValues[3]
                        
                        append(indent)
                        if (hideMarkers) {
                            pushStyle(boldStyle)
                            append("$num. ")
                            pop()
                        } else {
                            pushStyle(markerStyle)
                            append("$num. ")
                            pop()
                        }
                        parseInternal(content, hideMarkers, emptyList())
                    }
                    romanMatch != null -> {
                        val indent = romanMatch.groupValues[1]
                        val roman = romanMatch.groupValues[2]
                        val content = romanMatch.groupValues[3]
                        
                        append(indent)
                        if (hideMarkers) {
                            pushStyle(boldStyle)
                            append("$roman. ")
                            pop()
                        } else {
                            pushStyle(markerStyle)
                            append("$roman. ")
                            pop()
                        }
                        parseInternal(content, hideMarkers, emptyList())
                    }
                    else -> {
                        parseInternal(line, hideMarkers, emptyList())
                    }
                }
            }
        }
        parseCache.put(cacheKey, result)
        return result
    }

    private fun AnnotatedString.Builder.parseInternal(
        text: String,
        hideMarkers: Boolean,
        activeStyles: List<SpanStyle>
    ) {
        var i = 0
        val len = text.length

        while (i < len) {
            val ch = text[i]
            if (ch == '*' || ch == '_' || ch == '~' || ch == '`') {
                val closingIdx = findClosingMarker(text, ch, i + 1)
                if (closingIdx != -1 && closingIdx > i + 1) {
                    val content = text.substring(i + 1, closingIdx)
                    
                    // Style for the tag opening marker
                    if (!hideMarkers) {
                        pushStyle(markerStyle)
                        append(ch)
                        pop()
                    }

                    // Map marker to corresponding style
                    val currentStyle = when (ch) {
                        '*' -> boldStyle
                        '_' -> italicStyle
                        '~' -> strikeStyle
                        '`' -> codeStyle
                        else -> SpanStyle()
                    }

                    pushStyle(currentStyle)
                    if (ch == '`') {
                        // Monospace code blocks do not parse nested formatting
                        append(content)
                    } else {
                        // Recurse to handle nested styling
                        parseInternal(content, hideMarkers, activeStyles + currentStyle)
                    }
                    pop()

                    // Style for the tag closing marker
                    if (!hideMarkers) {
                        pushStyle(markerStyle)
                        append(ch)
                        pop()
                    }

                    i = closingIdx + 1
                    continue
                }
            }

            append(ch)
            i++
        }
    }

    private fun findClosingMarker(text: String, marker: Char, startIdx: Int): Int {
        var idx = startIdx
        val len = text.length
        while (idx < len) {
            val ch = text[idx]
            if (ch == marker) {
                // Must not be preceded by space or newline (e.g. "*bold *")
                // And the character immediately after opening marker (startIdx) must not be space or newline (e.g. "* bold*")
                val prevChar = text[idx - 1]
                val nextCharAfterStart = text.getOrNull(startIdx)
                if (prevChar != ' ' && prevChar != '\n' && nextCharAfterStart != ' ' && nextCharAfterStart != '\n') {
                    return idx
                }
            }
            idx++
        }
        return -1
    }

    fun incrementRoman(roman: String): String {
        val isUpper = roman.all { it.isUpperCase() }
        val normalRoman = roman.uppercase()
        val value = romanToDecimal(normalRoman)
        val nextValue = value + 1
        val nextRoman = decimalToRoman(nextValue)
        return if (isUpper) nextRoman else nextRoman.lowercase()
    }

    fun romanToDecimal(roman: String): Int {
        val map = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
        var decimal = 0
        var prev = 0
        for (i in roman.length - 1 downTo 0) {
            val current = map[roman[i]] ?: 0
            if (current < prev) {
                decimal -= current
            } else {
                decimal += current
            }
            prev = current
        }
        return decimal
    }

    fun decimalToRoman(num: Int): String {
        val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        val sb = java.lang.StringBuilder()
        var temp = num
        for (i in values.indices) {
            while (temp >= values[i]) {
                temp -= values[i]
                sb.append(symbols[i])
            }
        }
        return sb.toString()
    }
}
