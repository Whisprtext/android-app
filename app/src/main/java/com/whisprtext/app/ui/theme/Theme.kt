package com.whisprtext.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat

import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.whisprtext.app.data.model.AppearanceSettings
import com.whisprtext.app.data.model.ThemeMode

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline
)

@Composable
fun WhisprtextTheme(
    appearanceSettings: AppearanceSettings = AppearanceSettings(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appearanceSettings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val currentWhisprTheme = AppearancePresets.getTheme(appearanceSettings.presetId)

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme.copy(
            primary = currentWhisprTheme.primaryDark,
            onPrimary = if (currentWhisprTheme.primaryDark.luminance() > 0.5f) Color.Black else Color.White,
            primaryContainer = currentWhisprTheme.primaryDark.copy(alpha = 0.2f),
            onPrimaryContainer = currentWhisprTheme.primaryDark,
            secondaryContainer = currentWhisprTheme.primaryDark.copy(alpha = 0.15f),
            onSecondaryContainer = currentWhisprTheme.primaryDark,
            background = currentWhisprTheme.backgroundColorDark,
            surface = currentWhisprTheme.surfaceColorDark,
            onSurface = if (currentWhisprTheme.surfaceColorDark.luminance() > 0.5f) Color.Black else Color.White,
            onSurfaceVariant = (if (currentWhisprTheme.surfaceColorDark.luminance() > 0.5f) Color.Black else Color.White).copy(alpha = 0.7f),
            onBackground = if (currentWhisprTheme.backgroundColorDark.luminance() > 0.5f) Color.Black else Color.White
        )
        else -> LightColorScheme.copy(
            primary = currentWhisprTheme.primaryLight,
            onPrimary = if (currentWhisprTheme.primaryLight.luminance() > 0.5f) Color.Black else Color.White,
            primaryContainer = currentWhisprTheme.primaryLight.copy(alpha = 0.1f),
            onPrimaryContainer = currentWhisprTheme.primaryLight,
            secondaryContainer = currentWhisprTheme.primaryLight.copy(alpha = 0.1f),
            onSecondaryContainer = currentWhisprTheme.primaryLight,
            background = currentWhisprTheme.backgroundColorLight,
            surface = currentWhisprTheme.surfaceColorLight,
            onSurface = if (currentWhisprTheme.surfaceColorLight.luminance() > 0.5f) Color.Black else Color.White,
            onSurfaceVariant = (if (currentWhisprTheme.surfaceColorLight.luminance() > 0.5f) Color.Black else Color.White).copy(alpha = 0.7f),
            onBackground = if (currentWhisprTheme.backgroundColorLight.luminance() > 0.5f) Color.Black else Color.White
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val isLight = colorScheme.background.luminance() > 0.5f
            
            // Set the system bar colors to match background/surface
            // Use surface color for nav bar if it's not transparent/gradient-based
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = isLight
                isAppearanceLightNavigationBars = colorScheme.surface.luminance() > 0.5f
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}

