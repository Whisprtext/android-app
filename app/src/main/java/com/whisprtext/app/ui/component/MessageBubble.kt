package com.whisprtext.app.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisprtext.app.ui.theme.ChatTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    content: AnnotatedString,
    time: String,
    isSelf: Boolean,
    isGroupHeader: Boolean,
    isGroupFooter: Boolean,
    theme: ChatTheme,
    isDark: Boolean,
    syncStatus: String? = null,
    onLongClick: (() -> Unit)? = null
) {
    val bubbleColor = remember(isSelf, isDark, theme) {
        if (isSelf) {
            if (isDark) theme.selfBubbleColorDark else theme.selfBubbleColorLight
        } else {
            if (isDark) theme.otherBubbleColorDark else theme.otherBubbleColorLight
        }
    }
    val textColor = if (isDark) Color.White else Color.Black
    val alignment = if (isSelf) Alignment.End else Alignment.Start

    val bubbleShape = remember(isSelf, isGroupHeader, isGroupFooter) {
        RoundedCornerShape(
            topStart = if (!isSelf) 0.dp else (if (isGroupHeader) 12.dp else 4.dp),
            topEnd = if (isSelf) 0.dp else (if (isGroupHeader) 12.dp else 4.dp),
            bottomStart = if (!isSelf) 0.dp else (if (isGroupFooter) 12.dp else 4.dp),
            bottomEnd = if (isSelf) 0.dp else (if (isGroupFooter) 12.dp else 4.dp)
        )
    }

    val textMeasurer = rememberTextMeasurer()
    val textStyle = remember(textColor) {
        TextStyle(
            color = textColor,
            fontSize = 16.sp
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .graphicsLayer {
                    shape = bubbleShape
                    clip = true
                }
                .background(bubbleColor)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            onLongClick = onLongClick,
                            onClick = {}
                        )
                    } else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Layout(
                content = {
                    androidx.compose.foundation.text.BasicText(
                        text = content,
                        style = textStyle
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = time,
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.6f)
                        )
                        if (isSelf && syncStatus != null) {
                            val statusText = when (syncStatus) {
                                "pending" -> "🕒"
                                "failed" -> "✕"
                                "sent" -> "✓"
                                "delivered" -> "✓✓"
                                "read" -> "✓✓✓"
                                else -> "✓"
                            }
                            val statusColor = when (syncStatus) {
                                "failed" -> Color.Red
                                "read" -> Color(0xFF34B7F1)
                                else -> textColor.copy(alpha = 0.6f)
                            }
                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                color = statusColor
                            )
                        }
                    }
                }
            ) { measurables, constraints ->
                val textLayoutResult = textMeasurer.measure(
                    text = content,
                    style = textStyle,
                    constraints = constraints
                )
                
                val textPlaceable = measurables[0].measure(constraints)
                val timePlaceable = measurables[1].measure(constraints.copy(minWidth = 0))

                val parentWidth = constraints.maxWidth

                val lineCount = textLayoutResult.lineCount
                val lastLineRight = if (lineCount > 0) textLayoutResult.getLineRight(lineCount - 1) else 0f
                val lastLineLeft = if (lineCount > 0) textLayoutResult.getLineLeft(lineCount - 1) else 0f
                val lastLineWidth = lastLineRight - lastLineLeft

                val spacingPx = (8 * density).toInt()
                val fitsOnLastLine = lineCount > 0 && (lastLineWidth + spacingPx + timePlaceable.width <= parentWidth)

                val layoutHeight = if (fitsOnLastLine) {
                    maxOf(textPlaceable.height, timePlaceable.height)
                } else {
                    textPlaceable.height + timePlaceable.height
                }

                layout(parentWidth, layoutHeight) {
                    textPlaceable.placeRelative(0, 0)
                    timePlaceable.placeRelative(
                        x = parentWidth - timePlaceable.width,
                        y = layoutHeight - timePlaceable.height
                    )
                }
            }
        }
    }
}
