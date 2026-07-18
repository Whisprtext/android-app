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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun StatusAvatar(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    id: String = "default",
    showBorder: Boolean = true,
    showPlusIcon: Boolean = false,
    gradientStart: Color? = null,
    gradientEnd: Color? = null
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
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Status",
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 12.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            val backgroundModifier = if (gradientStart != null && gradientEnd != null) {
                Modifier.background(Brush.linearGradient(listOf(gradientStart, gradientEnd)))
            } else {
                val colors = listOf(
                    Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8), Color(0xFF9575CD),
                    Color(0xFF7986CB), Color(0xFF64B5F6), Color(0xFF4FC3F7), Color(0xFF4DB6AC),
                    Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFFB74D), Color(0xFFFF8A65)
                )
                val colorIndex = Math.abs(id.hashCode()) % colors.size
                Modifier.background(colors[colorIndex])
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(backgroundModifier),
                contentAlignment = Alignment.Center
            ) {
                if (!showPlusIcon) {
                    val initials = id.trimStart().filter { it.isLetter() }.take(1).uppercase()
                    Text(
                        text = initials,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = com.whisprtext.app.ui.theme.PoppinsFontFamily
                    )
                }
            }
        }

        if (showPlusIcon) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Status",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
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
