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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisprtext.app.ui.theme.IncomingBubbleShape
import com.whisprtext.app.ui.theme.InterFontFamily
import com.whisprtext.app.ui.theme.OutgoingBubbleShape
import com.whisprtext.app.ui.theme.WhisprTheme
import com.whisprtext.app.util.AccessibilityHelper
import com.whisprtext.app.util.EmojiStickerClassifier
import kotlinx.coroutines.launch

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
    shouldAnimate: Boolean = false,
    onAnimationComplete: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    mediaContent: @Composable (() -> Unit)? = null,
    mimeType: String? = null,
    attachmentUrl: String? = null
) {
    val context = LocalContext.current
    val reducedMotion = remember { AccessibilityHelper.isReducedMotionEnabled(context) }
    
    val animatedScale = remember { Animatable(if (shouldAnimate && !reducedMotion) 0.94f else 1.0f) }
    val animatedAlpha = remember { Animatable(if (shouldAnimate && !reducedMotion) 0f else 1f) }

    LaunchedEffect(shouldAnimate, reducedMotion) {
        if (shouldAnimate && !reducedMotion) {
            launch {
                animatedScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            launch {
                animatedAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 220)
                )
            }
            // Wait for duration roughly
            kotlinx.coroutines.delay(220)
            onAnimationComplete?.invoke()
        }
    }

    val isEmojiOnly = remember(content.text) { EmojiStickerClassifier.isEmojiOnly(content.text) }
    val isStickerOnly = remember(mimeType, content.text) { EmojiStickerClassifier.isStickerOnly(mimeType, content.text) }
    val isBorderlessSpecial = isEmojiOnly || isStickerOnly
    val effectiveShowBubbles = showBubbles && !isBorderlessSpecial

    val bubbleColor = if (isSelf) {
        if (isDark) theme.selfBubbleColorDark else theme.selfBubbleColorLight
    } else {
        if (isDark) theme.otherBubbleColorDark else theme.otherBubbleColorLight
    }

    val bubbleGradient = if (isSelf) {
        if (isDark) theme.selfBubbleGradientDark else theme.selfBubbleGradientLight
    } else {
        if (isDark) theme.otherBubbleGradientDark else theme.otherBubbleGradientLight
    }

    val onBubbleColor = remember(bubbleGradient, bubbleColor) {
        val checkColors = if (bubbleGradient.isNotEmpty()) bubbleGradient else listOf(bubbleColor)
        val avgLuminance = checkColors.map { 
            (0.299 * it.red + 0.587 * it.green + 0.114 * it.blue)
        }.average()
        if (avgLuminance > 0.6) Color.Black else Color.White
    }

    val textColor = if (effectiveShowBubbles) onBubbleColor else (if (isDark) Color.White else Color.Black)
    val alignment = if (isSelf) Alignment.End else Alignment.Start

    val textStyle = remember(textColor, isSelf) {
        TextStyle(
            color = textColor,
            fontSize = 16.sp,
            fontFamily = InterFontFamily,
            textAlign = if (isSelf) TextAlign.End else TextAlign.Start
        )
    }

    val isMediaOnly = (content.text.isEmpty() || content.text == "[Media]") && mediaContent != null
    val showTail = showTimestamp && !isMediaOnly && effectiveShowBubbles

    val shape = if (effectiveShowBubbles) {
        if (isMediaOnly) {
            RoundedCornerShape(20.dp)
        } else {
            if (isSelf) OutgoingBubbleShape(showTail) else IncomingBubbleShape(showTail)
        }
    } else RoundedCornerShape(0.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(horizontal = 12.dp, vertical = if (effectiveShowBubbles) 1.dp else 4.dp),
            horizontalAlignment = alignment
        ) {
            Surface(
                color = if (effectiveShowBubbles && !isMediaOnly && bubbleGradient.size < 2) bubbleColor else Color.Transparent,
                border = if (effectiveShowBubbles && isMediaOnly) androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)) else null,
                shape = shape,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = animatedScale.value
                        scaleY = animatedScale.value
                        alpha = animatedAlpha.value
                        transformOrigin = if (showTail) {
                            if (isSelf) TransformOrigin(1f, 1f) else TransformOrigin(0f, 0f)
                        } else TransformOrigin.Center
                    }
                    .then(
                        if (effectiveShowBubbles && !isMediaOnly && bubbleGradient.size >= 2) {
                            val startOffset = if (isSelf) androidx.compose.ui.geometry.Offset.Zero else androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f)
                            val endOffset = if (isSelf) androidx.compose.ui.geometry.Offset.Infinite else androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY)
                            Modifier.background(
                                brush = Brush.linearGradient(
                                    colors = bubbleGradient,
                                    start = startOffset,
                                    end = endOffset
                                ),
                                shape = shape
                            )
                        } else Modifier
                    )
                    .then(
                        if (onLongClick != null) {
                            Modifier.combinedClickable(
                                onLongClick = onLongClick,
                                onClick = {}
                            )
                        } else Modifier
                    )
            ) {
                if (isEmojiOnly) {
                    AnimatedEmojiMessage(text = content.text)
                } else if (isStickerOnly) {
                    AnimatedStickerMessage(
                        stickerUrlOrPath = attachmentUrl ?: content.text,
                        mimeType = mimeType
                    )
                } else {
                    val showText = content.text.isNotEmpty() && content.text != "[Media]"
                    ChatBubbleLayout(
                        isSelf = isSelf,
                        showBubbles = effectiveShowBubbles,
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
            }

            if (showTimestamp && effectiveShowBubbles) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .then(if (isSelf && showTail) Modifier.offset(y = (-8).dp) else Modifier)
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

            if (showTimestamp && !effectiveShowBubbles) {
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
