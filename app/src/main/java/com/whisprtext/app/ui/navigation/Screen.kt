package com.whisprtext.app.ui.navigation

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object ConversationList : Screen("conversations")
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
    object Settings : Screen("settings")
    object ContactDiscovery : Screen("contact_discovery")
    object Profile : Screen("profile?username={username}") {
        fun createRoute(username: String? = null) = if (username != null) "profile?username=$username" else "profile"
    }
}
