package com.whisprtext.app.data.model

data class AppearanceSettings(
    val presetId: String = "default",
    val useDoodles: Boolean = true,
    val doodleStyle: Int = 0,
    val doodleAlpha: Float = 0.1f
)
