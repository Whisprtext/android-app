package com.whisprtext.app.util

import android.content.Context
import android.provider.Settings

object AccessibilityHelper {

    /**
     * Returns true if system reduced motion / animator duration scale is disabled or set to 0.
     */
    fun isReducedMotionEnabled(context: Context): Boolean {
        return try {
            val durationScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            val transitionScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f
            )
            durationScale == 0.0f || transitionScale == 0.0f
        } catch (e: Exception) {
            false
        }
    }
}
