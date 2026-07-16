package com.whisprtext.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun StatusAvatar(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    id: String = "default",
    showBorder: Boolean = true,
    showPlusIcon: Boolean = false
) {
    // Telegram status style border (gradient)
    val statusGradient = Brush.sweepGradient(
        colors = listOf(
            Color(0xFF3498db),
            Color(0xFF2ecc71),
            Color(0xFFf1c40f),
            Color(0xFFe67e22),
            Color(0xFFe74c3c),
            Color(0xFF9b59b6),
            Color(0xFF3498db)
        )
    )

    Box(
        modifier = modifier
            .size(56.dp)
            .then(
                if (showBorder) {
                    Modifier.border(
                        width = 2.dp,
                        brush = statusGradient,
                        shape = CircleShape
                    )
                } else Modifier
            )
            .padding(if (showBorder) 4.dp else 0.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = avatarUrl ?: "https://picsum.photos/seed/${id.hashCode()}/200",
            contentDescription = "Status",
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 12.dp),
            contentScale = ContentScale.Crop
        )

        if (showPlusIcon) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Status",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .alpha(0.7f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StatusAvatarPreview() {
    StatusAvatar(avatarUrl = null)
}
