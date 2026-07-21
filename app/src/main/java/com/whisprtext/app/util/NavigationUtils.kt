package com.whisprtext.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/**
 * A debounced navigator to prevent duplicate navigation triggers which cause jittery animations.
 */
class DebouncedNavigator(private val navController: NavController) {
    private var lastNavTime = 0L
    private val debounceInterval = 300L // ms

    fun navigate(route: String, builder: NavOptionsBuilder.() -> Unit = {}) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNavTime > debounceInterval) {
            lastNavTime = currentTime
            navController.navigate(route, builder)
        }
    }

    fun popBackStack() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNavTime > debounceInterval) {
            lastNavTime = currentTime
            navController.popBackStack()
        }
    }
}

@Composable
fun rememberDebouncedNavigator(navController: NavController): DebouncedNavigator {
    return remember(navController) { DebouncedNavigator(navController) }
}
