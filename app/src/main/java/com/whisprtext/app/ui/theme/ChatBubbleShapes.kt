package com.whisprtext.app.ui.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

class IncomingBubbleShape(private val showTail: Boolean = true) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val w = size.width
            val h = size.height
            val tw = with(density) { 13.dp.toPx() } // Compact tail width
            val th = with(density) { 9.dp.toPx() }  // Reduced tail flick height
            val r = if (showTail) (h - th) / 2f else h / 2f
            
            if (showTail) {
                // Incoming: Tail pointer at TOP-LEFT (0, 0) - sharp tip
                moveTo(w - r, th)
                
                // Top edge leading towards tail
                lineTo(tw * 1.6f, th)
                
                // Top curve swooping UP into sharp flick tip (0, 0)
                cubicTo(
                    tw * 0.9f, th,
                    tw * 0.25f, th * 0.35f,
                    0f, 0f
                )
                
                // Bottom side of tail: smooth organic curve attaching into left side wall (tw)
                cubicTo(
                    tw * 0.2f, th * 0.5f,
                    tw * 0.85f, th + r * 0.3f,
                    tw, th + r * 0.7f
                )
                
                // Left side wall down to bottom-left corner
                lineTo(tw, h - r)
                
                // Bottom-left corner arc
                arcTo(Rect(tw, h - 2 * r, tw + 2 * r, h), 180f, -90f, false)
                
                // Bottom edge across to right side
                lineTo(w - r, h)
                
                // Right semi-circle arc (bottom-right to top-right)
                arcTo(Rect(w - 2 * r, th, w, h), 90f, -180f, false)
            } else {
                // Symmetric Pill
                val pillR = h / 2f
                moveTo(pillR, 0f)
                lineTo(w - pillR, 0f)
                arcTo(Rect(w - h, 0f, w, h), -90f, 180f, false)
                lineTo(pillR, h)
                arcTo(Rect(0f, 0f, h, h), 90f, 180f, false)
            }
            close()
        }
        return Outline.Generic(path)
    }
}

class OutgoingBubbleShape(private val showTail: Boolean = true) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val w = size.width
            val h = size.height
            val tw = with(density) { 13.dp.toPx() } // Compact tail width
            val th = with(density) { 9.dp.toPx() }  // Reduced tail flick height
            val r = if (showTail) (h - th) / 2f else h / 2f
            
            if (showTail) {
                // Outgoing: Tail pointer at BOTTOM-RIGHT (w, h) - sharp tip
                moveTo(r, 0f)
                
                // Top edge across to right side
                lineTo(w - tw - r, 0f)
                
                // Top-right rounded corner
                arcTo(Rect(w - tw - 2 * r, 0f, w - tw, 2 * r), 270f, 90f, false)
                
                // Right side wall down towards lower junction
                lineTo(w - tw, h - th - r * 0.7f)
                
                // Upper side of bottom-right tail: smooth organic curve to sharp tip (w, h)
                cubicTo(
                    w - tw, h - th - r * 0.3f,
                    w - tw * 0.2f, h - th * 0.5f,
                    w, h
                )
                
                // Bottom curve swooping from sharp tip (w, h) back up to flat bottom edge y = h - th
                cubicTo(
                    w - tw * 0.25f, h - th * 0.65f,
                    w - tw * 0.9f, h - th,
                    w - tw * 1.6f, h - th
                )
                
                // Flat bottom edge to left side
                lineTo(r, h - th)
                
                // Left semi-circle arc from (r, h - th) around to (r, 0)
                arcTo(Rect(0f, 0f, 2 * r, h - th), 90f, 180f, false)
            } else {
                // Symmetric Pill
                val pillR = h / 2f
                moveTo(pillR, 0f)
                lineTo(w - pillR, 0f)
                arcTo(Rect(w - h, 0f, w, h), -90f, 180f, false)
                lineTo(pillR, h)
                arcTo(Rect(0f, 0f, h, h), 90f, 180f, false)
            }
            close()
        }
        return Outline.Generic(path)
    }
}
