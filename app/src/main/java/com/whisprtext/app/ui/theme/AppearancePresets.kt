package com.whisprtext.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class WhisprTheme(
    val id: String,
    val name: String,
    val primaryLight: Color,
    val primaryDark: Color,
    val backgroundColorLight: Color,
    val backgroundColorDark: Color,
    val surfaceColorLight: Color,
    val surfaceColorDark: Color,
    val isGradient: Boolean = false,
    val gradientColorsLight: List<Color> = emptyList(),
    val gradientColorsDark: List<Color> = emptyList(),
    val selfBubbleColorLight: Color,
    val selfBubbleColorDark: Color,
    val otherBubbleColorLight: Color,
    val otherBubbleColorDark: Color
)

object AppearancePresets {
    val themes = listOf(
        WhisprTheme(
            id = "default",
            name = "Whispr Soft",
            primaryLight = Color(0xFF9155C1),
            primaryDark = Color(0xFFD7BAFF),
            backgroundColorLight = Color(0xFFFFF7FB),
            backgroundColorDark = Color(0xFF1C1B2E),
            surfaceColorLight = Color(0xFFF8F2FF),
            surfaceColorDark = Color(0xFF25233A),
            selfBubbleColorLight = Color(0xFFD7BAFF),
            selfBubbleColorDark = Color(0xFF673B91),
            otherBubbleColorLight = Color(0xFFF3EAF8),
            otherBubbleColorDark = Color(0xFF2D2A45)
        ),
        WhisprTheme(
            id = "whispr_pink",
            name = "Whispr Pink",
            primaryLight = Color(0xFFE06AAE),
            primaryDark = Color(0xFFF3A7D8),
            backgroundColorLight = Color(0xFFFFF5F7),
            backgroundColorDark = Color(0xFF2E1B25),
            surfaceColorLight = Color(0xFFFFF0F5),
            surfaceColorDark = Color(0xFF3D2531),
            selfBubbleColorLight = Color(0xFFF3A7D8),
            selfBubbleColorDark = Color(0xFF772B5B),
            otherBubbleColorLight = Color(0xFFFFE4F3),
            otherBubbleColorDark = Color(0xFF4A2D3C)
        ),
        WhisprTheme(
            id = "whispr_ocean",
            name = "Whispr Ocean",
            primaryLight = Color(0xFF5599C1),
            primaryDark = Color(0xFFA7D8FF),
            backgroundColorLight = Color(0xFFF0F9FF),
            backgroundColorDark = Color(0xFF1B262E),
            surfaceColorLight = Color(0xFFE6F4FF),
            surfaceColorDark = Color(0xFF25313D),
            selfBubbleColorLight = Color(0xFFA7D8FF),
            selfBubbleColorDark = Color(0xFF004C70),
            otherBubbleColorLight = Color(0xFFE1F5FE),
            otherBubbleColorDark = Color(0xFF2D3E4D)
        ),
        WhisprTheme(
            id = "whispr_cyan",
            name = "Whispr Cyan",
            primaryLight = Color(0xFF00ACC1),
            primaryDark = Color(0xFF80DEEA),
            backgroundColorLight = Color(0xFFF0FCFF),
            backgroundColorDark = Color(0xFF1B2E2E),
            surfaceColorLight = Color(0xFFE0F7FA),
            surfaceColorDark = Color(0xFF253D3D),
            selfBubbleColorLight = Color(0xFF80DEEA),
            selfBubbleColorDark = Color(0xFF006064),
            otherBubbleColorLight = Color(0xFFE0F7FA),
            otherBubbleColorDark = Color(0xFF2E4545)
        ),
        WhisprTheme(
            id = "whispr_lavender",
            name = "Whispr Lavender",
            primaryLight = Color(0xFF7E57C2),
            primaryDark = Color(0xFFB39DDB),
            backgroundColorLight = Color(0xFFF9F0FF),
            backgroundColorDark = Color(0xFF221B2E),
            surfaceColorLight = Color(0xFFF3E5F5),
            surfaceColorDark = Color(0xFF2D253D),
            selfBubbleColorLight = Color(0xFFB39DDB),
            selfBubbleColorDark = Color(0xFF512DA8),
            otherBubbleColorLight = Color(0xFFF1E6FF),
            otherBubbleColorDark = Color(0xFF382D4D)
        ),
        WhisprTheme(
            id = "sunset_gradient",
            name = "Whispr Sunset",
            primaryLight = Color(0xFFFF9800),
            primaryDark = Color(0xFFFF9800),
            backgroundColorLight = Color(0xFFFFE0B2),
            backgroundColorDark = Color(0xFF3E2723),
            surfaceColorLight = Color(0xFFFFCCBC),
            surfaceColorDark = Color(0xFF4E342E),
            isGradient = true,
            gradientColorsLight = listOf(Color(0xFFFFE0B2), Color(0xFFFFCCBC)),
            gradientColorsDark = listOf(Color(0xFF3E2723), Color(0xFF4E342E)),
            selfBubbleColorLight = Color(0xFFFF9800),
            selfBubbleColorDark = Color(0xFFE65100),
            otherBubbleColorLight = Color(0xFFFFF3E0),
            otherBubbleColorDark = Color(0xFF5D4037)
        )
    )

    fun getTheme(id: String): WhisprTheme {
        return themes.find { it.id == id } ?: themes[0]
    }
}
