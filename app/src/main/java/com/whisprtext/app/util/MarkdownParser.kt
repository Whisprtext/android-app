package com.whisprtext.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

object MarkdownParser {
    private val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
    private val italicStyle = SpanStyle(fontStyle = FontStyle.Italic)
    private val strikeStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)
    private val codeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = Color.LightGray.copy(alpha = 0.3f)
    )
    private val markerStyle = SpanStyle(color = Color.Gray.copy(alpha = 0.5f))

    fun parse(text: String, hideMarkers: Boolean): AnnotatedString {
        return buildAnnotatedString {
            parseInternal(text, hideMarkers, emptyList())
        }
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
}
