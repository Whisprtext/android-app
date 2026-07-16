package com.whisprtext.app.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun DoodleBorderBackground(
    style: Int,
    alpha: Float,
    color: Color = Color.Black,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val patternSizePx = remember(density) { with(density) { 80.dp.toPx() } }

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val path = if (style == 1) {
                    Path().apply {
                        moveTo(patternSizePx * 0.2f, patternSizePx * 0.8f)
                        lineTo(patternSizePx * 0.5f, patternSizePx * 0.2f)
                        lineTo(patternSizePx * 0.8f, patternSizePx * 0.8f)
                        close()
                    }
                } else null

                onDrawBehind {
                    val cols = (size.width / patternSizePx).toInt() + 1
                    val rows = (size.height / patternSizePx).toInt() + 1

                    for (i in 0 until cols) {
                        for (j in 0 until rows) {
                            val x = i * patternSizePx
                            val y = j * patternSizePx

                            // Calculate if this cell is on the border
                            // We use a margin of about 1 pattern size
                            val isBorder = i == 0 || i >= cols - 1 || j == 0 || j >= rows - 1
                            if (!isBorder) continue

                            // Add a bit of random offset to make it look hand-drawn
                            val offsetX = (i * 31 % 17 - 8).toFloat()
                            val offsetY = (j * 37 % 13 - 6).toFloat()

                            translate(offsetX, offsetY) {
                                when (style) {
                                    0 -> { // Dots and small circles
                                        drawCircle(
                                            color = color,
                                            radius = 2f,
                                            center = Offset(x + patternSizePx / 2, y + patternSizePx / 2),
                                            alpha = alpha
                                        )
                                        drawCircle(
                                            color = color,
                                            radius = 4f,
                                            center = Offset(x + patternSizePx * 0.2f, y + patternSizePx * 0.3f),
                                            alpha = alpha * 0.5f,
                                            style = Stroke(width = 1f)
                                        )
                                    }
                                    1 -> { // Small Triangles and lines
                                        translate(x, y) {
                                            path?.let {
                                                drawPath(
                                                    path = it,
                                                    color = color,
                                                    alpha = alpha,
                                                    style = Stroke(width = 1.5f)
                                                )
                                            }
                                            drawLine(
                                                color = color,
                                                start = Offset(patternSizePx * 0.1f, patternSizePx * 0.1f),
                                                end = Offset(patternSizePx * 0.3f, patternSizePx * 0.2f),
                                                alpha = alpha * 0.7f,
                                                strokeWidth = 1f
                                            )
                                        }
                                    }
                                    2 -> { // Wavy lines and curves
                                        drawArc(
                                            color = color,
                                            startAngle = 0f,
                                            sweepAngle = 180f,
                                            useCenter = false,
                                            topLeft = Offset(x + patternSizePx * 0.1f, y + patternSizePx * 0.4f),
                                            size = Size(patternSizePx * 0.6f, patternSizePx * 0.3f),
                                            alpha = alpha,
                                            style = Stroke(width = 1.5f)
                                        )
                                        drawArc(
                                            color = color,
                                            startAngle = 180f,
                                            sweepAngle = 180f,
                                            useCenter = false,
                                            topLeft = Offset(x + patternSizePx * 0.4f, y + patternSizePx * 0.2f),
                                            size = Size(patternSizePx * 0.4f, patternSizePx * 0.2f),
                                            alpha = alpha * 0.6f,
                                            style = Stroke(width = 1f)
                                        )
                                    }
                                    3 -> { // Squares, pluses and dots
                                        drawRect(
                                            color = color,
                                            topLeft = Offset(x + patternSizePx * 0.2f, y + patternSizePx * 0.2f),
                                            size = Size(patternSizePx * 0.2f, patternSizePx * 0.2f),
                                            alpha = alpha,
                                            style = Stroke(width = 1f)
                                        )
                                        // Plus sign
                                        val plusSize = patternSizePx * 0.15f
                                        val plusCenterX = x + patternSizePx * 0.7f
                                        val plusCenterY = y + patternSizePx * 0.3f
                                        drawLine(
                                            color = color,
                                            start = Offset(plusCenterX - plusSize/2, plusCenterY),
                                            end = Offset(plusCenterX + plusSize/2, plusCenterY),
                                            alpha = alpha * 0.8f,
                                            strokeWidth = 1f
                                        )
                                        drawLine(
                                            color = color,
                                            start = Offset(plusCenterX, plusCenterY - plusSize/2),
                                            end = Offset(plusCenterX, plusCenterY + plusSize/2),
                                            alpha = alpha * 0.8f,
                                            strokeWidth = 1f
                                        )
                                        drawCircle(
                                            color = color,
                                            radius = 1.5f,
                                            center = Offset(x + patternSizePx * 0.7f, y + patternSizePx * 0.7f),
                                            alpha = alpha
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
    )
}
