package com.whisprtext.app.ui.navigation

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object ConversationList : Screen("conversations")
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
    object Settings : Screen("settings")
}
