package com.whisprtext.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutSlowInEasing

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

    // Easings
    val StandardEasing: Easing = FastOutSlowInEasing
    val DecelerateEasing: Easing = LinearOutSlowInEasing
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedAccelerateEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val EmphasizedDecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
}
