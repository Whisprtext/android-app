package com.whisprtext.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutSlowInEasing

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

object Motion {
    // Durations
    const val ShortDuration1 = 100
    const val ShortDuration2 = 150
    const val MediumDuration1 = 200
    const val MediumDuration2 = 250
    const val LongDuration1 = 300
    const val LongDuration2 = 350
    const val LongDuration3 = 400
    const val LongDuration4 = 500

    // Screen transition durations & factors
    const val ScreenSlideDuration = 480
    const val ScreenParallaxFactor = 0.0f

    // Easings
    val StandardEasing: Easing = FastOutSlowInEasing
    val DecelerateEasing: Easing = LinearOutSlowInEasing
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedAccelerateEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val EmphasizedDecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    
    // Ultra-smooth frame-rate independent fluid curve for screen transitions across 60Hz - 144Hz
    val ScreenSlideEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    fun <T> screenSlideSpec(): androidx.compose.animation.core.FiniteAnimationSpec<T> =
        androidx.compose.animation.core.tween(ScreenSlideDuration, easing = ScreenSlideEasing)

    // Physics spring for frame-by-frame VSYNC interpolation
    fun <T> screenSlideSpringSpec(): SpringSpec<T> = spring(
        stiffness = 380f,
        dampingRatio = 0.92f
    )
}
