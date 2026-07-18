package com.whisprtext.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whisprtext.app.WhisprTextApp
import com.whisprtext.app.ui.theme.PoppinsFontFamily
import com.whisprtext.app.util.AvatarUrlResolver

/**
 * Reusable circular user avatar.
 *
 * - Shows a remote photo when [avatarUrl] is a resolvable reference.
 * - Falls back to the existing dynamic-initials avatar when no custom avatar exists
 *   or when remote loading fails.
 */
@Composable
fun UserAvatar(
    id: String,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    fontSize: TextUnit = 16.sp,
    gradientStart: Color? = null,
    gradientEnd: Color? = null,
    contentDescription: String? = null
) {
    val initials = remember(id) {
        id.trimStart().filter { it.isLetter() }.take(1).uppercase().ifEmpty { "?" }
    }

    val backgroundModifier = remember(id, gradientStart, gradientEnd) {
        if (gradientStart != null && gradientEnd != null) {
            Modifier.background(
                brush = Brush.linearGradient(colors = listOf(gradientStart, gradientEnd)),
                shape = CircleShape
            )
        } else {
            val colors = listOf(
                Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8), Color(0xFF9575CD),
                Color(0xFF7986CB), Color(0xFF64B5F6), Color(0xFF4FC3F7), Color(0xFF4DB6AC),
                Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFFB74D), Color(0xFFFF8A65)
            )
            val colorIndex = Math.abs(id.hashCode()) % colors.size
            Modifier.background(colors[colorIndex], shape = CircleShape)
        }
    }

    val context = LocalContext.current
    val apiClient = remember(context) {
        (context.applicationContext as? WhisprTextApp)?.apiClient
    }

    var resolvedUrl by remember(avatarUrl) { mutableStateOf<String?>(null) }
    var isError by remember(avatarUrl) { mutableStateOf(false) }

    LaunchedEffect(avatarUrl, apiClient) {
        isError = false
        resolvedUrl = null
        if (!AvatarUrlResolver.isRemoteAvatarRef(avatarUrl)) {
            return@LaunchedEffect
        }
        if (AvatarUrlResolver.isStorageRef(avatarUrl)) {
            if (apiClient == null) {
                isError = true
                return@LaunchedEffect
            }
            resolvedUrl = AvatarUrlResolver.resolve(apiClient, avatarUrl)
            if (resolvedUrl == null) {
                isError = true
            }
        } else {
            // Legacy http(s) URLs load directly.
            resolvedUrl = avatarUrl
        }
    }

    Box(
        modifier = modifier
            .then(backgroundModifier)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = fontSize,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold
        )

        val loadUrl = resolvedUrl
        if (!loadUrl.isNullOrBlank() && !isError) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(loadUrl)
                    .crossfade(true)
                    // Include avatar ref in memory key so revision changes bust cache.
                    .memoryCacheKey(avatarUrl)
                    .diskCacheKey(avatarUrl)
                    .build(),
                contentDescription = contentDescription ?: id,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { isError = true }
            )
        }
    }
}

/**
 * Backward-compatible alias used by existing call sites and tests.
 * Prefer [UserAvatar] for new code.
 */
@Composable
fun InitialsAvatar(
    id: String,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    fontSize: TextUnit = 16.sp,
    gradientStart: Color? = null,
    gradientEnd: Color? = null
) {
    UserAvatar(
        id = id,
        modifier = modifier,
        avatarUrl = avatarUrl,
        fontSize = fontSize,
        gradientStart = gradientStart,
        gradientEnd = gradientEnd
    )
}
