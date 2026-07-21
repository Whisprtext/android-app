package com.whisprtext.app.ui.component

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.airbnb.lottie.compose.*
import com.whisprtext.app.util.AccessibilityHelper

@Composable
fun AnimatedStickerMessage(
    stickerUrlOrPath: String,
    mimeType: String?,
    modifier: Modifier = Modifier,
    maxSize: Dp = 160.dp
) {
    val context = LocalContext.current
    val isReducedMotion = AccessibilityHelper.isReducedMotionEnabled(context)

    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    val isLottie = mimeType == "application/json+lottie" || stickerUrlOrPath.endsWith(".json")

    Box(
        modifier = modifier
            .sizeIn(maxWidth = maxSize, maxHeight = maxSize)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLottie) {
            val composition by rememberLottieComposition(
                if (stickerUrlOrPath.startsWith("http")) {
                    LottieCompositionSpec.Url(stickerUrlOrPath)
                } else {
                    LottieCompositionSpec.Asset(stickerUrlOrPath.removePrefix("asset://"))
                }
            )
            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = if (isReducedMotion) 1 else LottieConstants.IterateForever,
                isPlaying = !isReducedMotion
            )
            LottieAnimation(
                composition = composition,
                progress = { if (isReducedMotion) 0f else progress },
                modifier = Modifier.size(maxSize)
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(stickerUrlOrPath)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = "Animated Sticker",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(maxSize)
            )
        }
    }
}
