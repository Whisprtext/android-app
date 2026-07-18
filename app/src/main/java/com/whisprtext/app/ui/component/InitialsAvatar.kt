package com.whisprtext.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.whisprtext.app.ui.theme.PoppinsFontFamily
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage

@Composable
fun InitialsAvatar(
    id: String,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    fontSize: TextUnit = 16.sp,
    gradientStart: Color? = null,
    gradientEnd: Color? = null
) {
    val initials = id.trimStart().filter { it.isLetter() }.take(1).uppercase()
    
    val backgroundModifier = if (gradientStart != null && gradientEnd != null) {
        Modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(gradientStart, gradientEnd)
            ),
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

    var isError by remember(avatarUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .then(backgroundModifier)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Show initials as a fallback/placeholder
        Text(
            text = initials,
            color = Color.White,
            fontSize = fontSize,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold
        )

        if (!avatarUrl.isNullOrBlank() && !isError) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = id,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { isError = true }
            )
        }
    }
}
