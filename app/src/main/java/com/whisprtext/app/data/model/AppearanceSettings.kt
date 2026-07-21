package com.whisprtext.app.data.model

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val presetId: String = "default",
    val showChatBubbles: Boolean = true
)
