package com.whisprtext.app.util

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun testPlainInput() {
        val result = MarkdownParser.parse("Hello World", hideMarkers = false)
        assertEquals("Hello World", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun testBoldWithMarkers() {
        val result = MarkdownParser.parse("Hello *World*", hideMarkers = false)
        assertEquals("Hello *World*", result.text)
        
        val boldSpan = result.spanStyles.find { it.item.fontWeight == FontWeight.Bold }
        org.junit.Assert.assertNotNull(boldSpan)
        assertEquals(7, boldSpan!!.start)
        assertEquals(12, boldSpan.end)
    }

    @Test
    fun testBoldHiddenMarkers() {
        val result = MarkdownParser.parse("Hello *World*", hideMarkers = true)
        assertEquals("Hello World", result.text)
        assertEquals(1, result.spanStyles.size)
        val boldSpan = result.spanStyles[0]
        assertEquals(FontWeight.Bold, boldSpan.item.fontWeight)
        assertEquals(6, boldSpan.start)
        assertEquals(11, boldSpan.end)
    }

    @Test
    fun testInvalidBold() {
        val result1 = MarkdownParser.parse("Hello * World*", hideMarkers = false)
        assertEquals("Hello * World*", result1.text)
        assertTrue(result1.spanStyles.isEmpty())

        val result2 = MarkdownParser.parse("Hello *World *", hideMarkers = false)
        assertEquals("Hello *World *", result2.text)
        assertTrue(result2.spanStyles.isEmpty())
    }

    @Test
    fun testNestedStyles() {
        val result = MarkdownParser.parse("nested _*bold italic*_", hideMarkers = true)
        assertEquals("nested bold italic", result.text)
        
        val boldSpan = result.spanStyles.find { it.item.fontWeight == FontWeight.Bold }
        val italicSpan = result.spanStyles.find { it.item.fontStyle == FontStyle.Italic }
        
        org.junit.Assert.assertNotNull(boldSpan)
        org.junit.Assert.assertNotNull(italicSpan)
        
        assertEquals(7, boldSpan!!.start)
        assertEquals(18, boldSpan.end)
        assertEquals(7, italicSpan!!.start)
        assertEquals(18, italicSpan.end)
    }

    @Test
    fun testCodeBlockNoNesting() {
        val result = MarkdownParser.parse("code `*not bold*`", hideMarkers = true)
        assertEquals("code *not bold*", result.text)
        
        val boldSpan = result.spanStyles.find { it.item.fontWeight == FontWeight.Bold }
        org.junit.Assert.assertNull(boldSpan)
    }

    @Test
    fun testBulletList() {
        val input = "- item 1\n* item 2\nnormal line"
        
        val resultHide = MarkdownParser.parse(input, hideMarkers = true)
        assertEquals("•  item 1\n•  item 2\nnormal line", resultHide.text)
        
        val resultShow = MarkdownParser.parse(input, hideMarkers = false)
        assertEquals("- item 1\n* item 2\nnormal line", resultShow.text)
    }

    @Test
    fun testNumberedList() {
        val input = "1. item 1\n2. item 2"
        
        val resultHide = MarkdownParser.parse(input, hideMarkers = true)
        assertEquals("1. item 1\n2. item 2", resultHide.text)
        
        val resultShow = MarkdownParser.parse(input, hideMarkers = false)
        assertEquals("1. item 1\n2. item 2", resultShow.text)
    }

    @Test
    fun testRomanList() {
        val input = "i. item 1\nii. item 2\nIX. item 9"
        
        val resultHide = MarkdownParser.parse(input, hideMarkers = true)
        assertEquals("i. item 1\nii. item 2\nIX. item 9", resultHide.text)
        
        val resultShow = MarkdownParser.parse(input, hideMarkers = false)
        assertEquals("i. item 1\nii. item 2\nIX. item 9", resultShow.text)
    }

    @Test
    fun testRomanArithmetic() {
        assertEquals("ii", MarkdownParser.incrementRoman("i"))
        assertEquals("iv", MarkdownParser.incrementRoman("iii"))
        assertEquals("x", MarkdownParser.incrementRoman("ix"))
        assertEquals("X", MarkdownParser.incrementRoman("IX"))
        assertEquals("mcmxciv", MarkdownParser.incrementRoman("mcmxciii"))
    }
}
