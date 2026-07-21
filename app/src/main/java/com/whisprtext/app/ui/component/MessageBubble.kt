package com.whisprtext.app.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisprtext.app.ui.theme.WhisprTheme
import com.whisprtext.app.ui.theme.IncomingBubbleShape
import com.whisprtext.app.ui.theme.OutgoingBubbleShape
import com.whisprtext.app.ui.theme.InterFontFamily
import com.whisprtext.app.ui.theme.Motion

import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    modifier: Modifier = Modifier,
    content: AnnotatedString,
    time: String,
    isSelf: Boolean,
    theme: WhisprTheme,
    isDark: Boolean,
    showBubbles: Boolean = true,
    syncStatus: String? = null,
    showTimestamp: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    mediaContent: @Composable (() -> Unit)? = null
) {
    val bubbleColor = if (isSelf) {
        if (isDark) theme.selfBubbleColorDark else theme.selfBubbleColorLight
    } else {
        if (isDark) theme.otherBubbleColorDark else theme.otherBubbleColorLight
    }

    val onBubbleColor = remember(bubbleColor) {
        // Simple luminance check for text contrast
        val luminance = (0.299 * bubbleColor.red + 0.587 * bubbleColor.green + 0.114 * bubbleColor.blue)
        if (luminance > 0.5) Color.Black else Color.White
    }

    val textColor = if (showBubbles) onBubbleColor else (if (isDark) Color.White else Color.Black)
    val alignment = if (isSelf) Alignment.End else Alignment.Start

    val textStyle = remember(textColor, isSelf) {
        TextStyle(
            color = textColor,
            fontSize = 16.sp,
            fontFamily = InterFontFamily,
            textAlign = if (isSelf) TextAlign.End else TextAlign.Start
        )
    }

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(Motion.MediumDuration2, easing = Motion.DecelerateEasing)
        )
    }

    val isMediaOnly = (content.text.isEmpty() || content.text == "[Media]") && mediaContent != null
    val showTail = showTimestamp && !isMediaOnly

    val shape = if (showBubbles) {
        if (isMediaOnly) {
            RoundedCornerShape(20.dp)
        } else {
            if (isSelf) OutgoingBubbleShape(showTail) else IncomingBubbleShape(showTail)
        }
    } else RoundedCornerShape(0.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedProgress.value
                translationX = (if (isSelf) 20.dp else (-20).dp).toPx() * (1f - animatedProgress.value)
            },
        horizontalAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(horizontal = 12.dp, vertical = if (showBubbles) 2.dp else 4.dp),
            horizontalAlignment = alignment
        ) {
            Surface(
                color = if (showBubbles) {
                    if (isMediaOnly) Color.Transparent else bubbleColor
                } else Color.Transparent,
                border = if (showBubbles && isMediaOnly) androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)) else null,
                shape = shape,
                modifier = Modifier
                    .then(
                        if (onLongClick != null) {
                            Modifier.combinedClickable(
                                onLongClick = onLongClick,
                                onClick = {}
                            )
                        } else Modifier
                    )
            ) {
                val showText = content.text.isNotEmpty() && content.text != "[Media]"
                ChatBubbleLayout(
                    isSelf = isSelf,
                    showBubbles = showBubbles,
                    isMediaOnly = isMediaOnly,
                    showTail = showTail,
                    tailWidth = 18.dp,
                    horizontalPadding = if (isMediaOnly) 0.dp else 2.dp,
                    verticalPadding = if (isMediaOnly) 0.dp else 10.dp,
                    messageContent = {
                        Column(horizontalAlignment = alignment) {
                            if (mediaContent != null) {
                                Box(modifier = Modifier.padding(bottom = if (showText) 6.dp else 0.dp)) {
                                    mediaContent()
                                }
                            }
                            if (showText) {
                                Text(
                                    text = content,
                                    style = textStyle,
                                    modifier = Modifier.wrapContentWidth()
                                )
                            }
                        }
                    }
                )
            }

            if (showTimestamp && showBubbles) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = time,
                        fontSize = 10.sp,
                        color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                    if (isSelf && syncStatus != null) {
                        ChatStatusIndicator(syncStatus, (if (isDark) Color.White else Color.Black).copy(alpha = 0.5f))
                    }
                }
            }

            if (showTimestamp && !showBubbles) {
                Spacer(modifier = Modifier.height(2.dp))
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
                        ChatStatusIndicator(syncStatus, textColor.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleLayout(
    isSelf: Boolean,
    showBubbles: Boolean,
    isMediaOnly: Boolean,
    showTail: Boolean,
    tailWidth: Dp,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    messageContent: @Composable () -> Unit
) {
    Layout(
        content = {
            Box(Modifier.layoutId("content")) { messageContent() }
        }
    ) { measurables, constraints ->
        val tw = if (showTail) tailWidth.toPx() else 0f
        val th = if (showTail) 10.dp.toPx() else 0f
        val hp = horizontalPadding.toPx()
        val vp = verticalPadding.toPx()

        // Estimate overhead for semi-circular ends.
        val estimatedPillDiameter = if (isMediaOnly) 0f else 100f 
        val overheadX = tw + (if (isMediaOnly) 0f else 2 * hp + estimatedPillDiameter)
        
        val contentConstraints = constraints.copy(
            maxWidth = (constraints.maxWidth - overheadX.toInt()).coerceAtLeast(0)
        )
        val contentPlaceable = measurables.find { it.layoutId == "content" }!!.measure(contentConstraints)

        if (!showBubbles) {
            layout(contentPlaceable.width, contentPlaceable.height) {
                contentPlaceable.place(0, 0)
            }
        } else {
            val h = contentPlaceable.height + 2 * vp + th
            val pillRadius = if (isMediaOnly) 0f else (h - th) / 2f
            
            // Total width perfectly wraps the text + padding + semi-circles + tail
            val totalWidth = contentPlaceable.width + tw + (2 * pillRadius) + (2 * hp)

            layout(totalWidth.toInt(), h.toInt()) {
                val xInPill = hp + pillRadius
                val contentX = if (isSelf) xInPill else (tw + xInPill)
                val contentY = if (isSelf) vp else (vp + th)
                contentPlaceable.placeRelative(contentX.toInt(), contentY.toInt())
            }
        }
    }
}

@Composable
private fun ChatStatusIndicator(syncStatus: String, color: Color) {
    val statusText = when (syncStatus) {
        "pending" -> "🕒"
        "queued" -> "✓"
        "failed" -> "✕"
        "sent" -> "✓"
        "delivered" -> "✓✓"
        "read" -> "✓✓✓"
        else -> "✓"
    }
    val statusColor = when (syncStatus) {
        "failed" -> Color.Red
        "read" -> Color(0xFF34B7F1)
        else -> color
    }
    Text(
        text = statusText,
        fontSize = 11.sp,
        color = statusColor
    )
}

