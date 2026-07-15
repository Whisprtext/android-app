package com.whisprtext.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun DoodleBackground(
    style: Int,
    alpha: Float,
    color: Color = Color.Gray,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val patternSize = 100f
        val cols = (size.width / patternSize).toInt() + 1
        val rows = (size.height / patternSize).toInt() + 1

        for (i in 0 until cols) {
            for (j in 0 until rows) {
                val x = i * patternSize
                val y = j * patternSize
                
                when (style) {
                    0 -> { // Dots
                        drawCircle(
                            color = color,
                            radius = 2f,
                            center = Offset(x + patternSize / 2, y + patternSize / 2),
                            alpha = alpha
                        )
                    }
                    1 -> { // Small Triangles
                        drawPath(
                            path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(x + 20f, y + 80f)
                                lineTo(x + 50f, y + 20f)
                                lineTo(x + 80f, y + 80f)
                                close()
                            },
                            color = color,
                            alpha = alpha,
                            style = Stroke(width = 2f)
                        )
                    }
                    2 -> { // Wavy lines
                        drawArc(
                            color = color,
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(x + 10f, y + 40f),
                            size = Size(80f, 40f),
                            alpha = alpha,
                            style = Stroke(width = 2f)
                        )
                    }
                    3 -> { // Squares and dots
                        drawRect(
                            color = color,
                            topLeft = Offset(x + 20f, y + 20f),
                            size = Size(20f, 20f),
                            alpha = alpha,
                            style = Stroke(width = 1f)
                        )
                        drawCircle(
                            color = color,
                            radius = 1.5f,
                            center = Offset(x + 70f, y + 70f),
                            alpha = alpha
                        )
                    }
                }
            }
        }
    }
}
