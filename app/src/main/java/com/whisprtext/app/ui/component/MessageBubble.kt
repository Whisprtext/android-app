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
import com.whisprtext.app.ui.theme.InterFontFamily
import com.whisprtext.app.ui.theme.Motion

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    content: AnnotatedString,
    time: String,
    isSelf: Boolean,
    isGroupHeader: Boolean,
    isGroupFooter: Boolean,
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
            textAlign = TextAlign.Start
        )
    }

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(Motion.MediumDuration2, easing = Motion.DecelerateEasing)
        )
    }

    val cornerRadius = 18.dp
    val sharpRadius = 4.dp
    
    val shape = if (showBubbles) {
        RoundedCornerShape(
            topStart = if (isSelf || isGroupHeader) cornerRadius else sharpRadius,
            topEnd = if (!isSelf || isGroupHeader) cornerRadius else sharpRadius,
            bottomStart = if (isSelf || isGroupFooter) cornerRadius else sharpRadius,
            bottomEnd = if (!isSelf || isGroupFooter) cornerRadius else sharpRadius
        )
    } else RoundedCornerShape(0.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedProgress.value
                translationX = (if (isSelf) 20.dp else (-20).dp).toPx() * (1f - animatedProgress.value)
            },
        horizontalAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(horizontal = 12.dp, vertical = if (showBubbles) 2.dp else 4.dp),
            horizontalAlignment = alignment
        ) {
            Surface(
                color = if (showBubbles) bubbleColor else Color.Transparent,
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
                Column(
                    modifier = Modifier.padding(
                        horizontal = if (showBubbles) 12.dp else 0.dp,
                        vertical = if (showBubbles) 8.dp else 0.dp
                    ),
                    horizontalAlignment = alignment
                ) {
                    if (mediaContent != null) {
                        Box(modifier = Modifier.padding(bottom = if (content.text.isNotEmpty()) 6.dp else 0.dp)) {
                            mediaContent()
                        }
                    }

                    val showText = content.text.isNotEmpty() && content.text != "[Media]"
                    if (showText) {
                        Text(
                            text = content,
                            style = textStyle,
                            modifier = Modifier.wrapContentWidth()
                        )
                    }

                    if (showTimestamp && showBubbles) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = time,
                                fontSize = 10.sp,
                                color = textColor.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium
                            )
                            if (isSelf && syncStatus != null) {
                                ChatStatusIndicator(syncStatus, textColor.copy(alpha = 0.7f))
                            }
                        }
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

