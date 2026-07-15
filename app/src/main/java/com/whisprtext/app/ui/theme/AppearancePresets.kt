package com.whisprtext.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class ChatTheme(
    val id: String,
    val name: String,
    val backgroundColorLight: Color,
    val backgroundColorDark: Color,
    val isGradient: Boolean = false,
    val gradientColorsLight: List<Color> = emptyList(),
    val gradientColorsDark: List<Color> = emptyList(),
    val selfBubbleColorLight: Color = Color(0xFFD1E7FF),
    val selfBubbleColorDark: Color = Color(0xFF004A77),
    val otherBubbleColorLight: Color = Color(0xFFF0F0F0),
    val otherBubbleColorDark: Color = Color(0xFF333333)
)

object AppearancePresets {
    val themes = listOf(
        ChatTheme(
            id = "default",
            name = "Classic Blue",
            backgroundColorLight = Color(0xFFF5F7FB),
            backgroundColorDark = Color(0xFF121212),
            selfBubbleColorLight = Color(0xFF007AFF),
            selfBubbleColorDark = Color(0xFF0056B3)
        ),
        ChatTheme(
            id = "pastel_pink",
            name = "Sweet Rose",
            backgroundColorLight = Color(0xFFFFF0F5),
            backgroundColorDark = Color(0xFF2D1B22),
            selfBubbleColorLight = Color(0xFFFFB6C1),
            selfBubbleColorDark = Color(0xFF8B4B5D)
        ),
        ChatTheme(
            id = "pastel_green",
            name = "Mint Fresh",
            backgroundColorLight = Color(0xFFF0FFF0),
            backgroundColorDark = Color(0xFF1B2D1B),
            selfBubbleColorLight = Color(0xFF98FB98),
            selfBubbleColorDark = Color(0xFF2E7D32)
        ),
        ChatTheme(
            id = "pastel_purple",
            name = "Lavender",
            backgroundColorLight = Color(0xFFF8F0FF),
            backgroundColorDark = Color(0xFF241B2D),
            selfBubbleColorLight = Color(0xFFE6E6FA),
            selfBubbleColorDark = Color(0xFF5E35B1)
        ),
        ChatTheme(
            id = "sunset_gradient",
            name = "Sunset",
            backgroundColorLight = Color.Transparent,
            backgroundColorDark = Color.Transparent,
            isGradient = true,
            gradientColorsLight = listOf(Color(0xFFFFE0B2), Color(0xFFFFCCBC)),
            gradientColorsDark = listOf(Color(0xFF3E2723), Color(0xFF4E342E)),
            selfBubbleColorLight = Color(0xFFFF9800),
            selfBubbleColorDark = Color(0xFFE65100)
        ),
        ChatTheme(
            id = "ocean_gradient",
            name = "Ocean",
            backgroundColorLight = Color.Transparent,
            backgroundColorDark = Color.Transparent,
            isGradient = true,
            gradientColorsLight = listOf(Color(0xFFB2EBF2), Color(0xFF80DEEA)),
            gradientColorsDark = listOf(Color(0xFF006064), Color(0xFF00838F)),
            selfBubbleColorLight = Color(0xFF00BCD4),
            selfBubbleColorDark = Color(0xFF0097A7)
        )
    )

    fun getTheme(id: String): ChatTheme {
        return themes.find { it.id == id } ?: themes[0]
    }
}
