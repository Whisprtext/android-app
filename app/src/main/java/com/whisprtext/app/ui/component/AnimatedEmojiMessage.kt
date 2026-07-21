package com.whisprtext.app.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisprtext.app.util.AccessibilityHelper
import com.whisprtext.app.util.EmojiStickerClassifier

@Composable
fun AnimatedEmojiMessage(
    text: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isReducedMotion = AccessibilityHelper.isReducedMotionEnabled(context)
    val count = EmojiStickerClassifier.countEmojis(text)

    val fontSize = when (count) {
        1 -> 52.sp
        2, 3 -> 42.sp
        else -> 32.sp
    }

    val infiniteTransition = rememberInfiniteTransition(label = "EmojiPulseTransition")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isReducedMotion) 1.0f else 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EmojiPulseScale"
    )

    Box(
        modifier = modifier
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .scale(if (isReducedMotion) 1.0f else scale),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            lineHeight = fontSize * 1.2f,
            textAlign = TextAlign.Center
        )
    }
}
