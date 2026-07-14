package com.whisprtext.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.whisprtext.app.ui.navigation.Screen
import com.whisprtext.app.ui.screen.AuthScreen
import com.whisprtext.app.ui.screen.ChatScreen
import com.whisprtext.app.ui.screen.ConversationListScreen
import com.whisprtext.app.ui.screen.SettingsScreen
import com.whisprtext.app.ui.screen.ProfileScreen
import com.whisprtext.app.ui.theme.WhisrtextTheme
import com.whisprtext.app.ui.viewmodel.AuthViewModel
import com.whisprtext.app.ui.viewmodel.ChatViewModel
import com.whisprtext.app.ui.viewmodel.ConversationsViewModel
import com.whisprtext.app.ui.viewmodel.SettingsViewModel
import com.whisprtext.app.ui.viewmodel.ProfileViewModel

import android.util.Log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val app = application as WhisprTextApp
        val preferencesManager = app.preferencesManager
        val apiClient = app.apiClient
        val chatRepository = app.chatRepository

        setContent {
            WhisrtextTheme {
                var isSessionLoaded by remember { mutableStateOf(false) }
                var sessionToken by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(preferencesManager) {
                    Log.d("MainActivity", "LaunchedEffect: starting preference observation")
                    preferencesManager.sessionToken.collect { token ->
                        Log.d("MainActivity", "LaunchedEffect observed sessionToken: '$token'")
                        sessionToken = token
                        isSessionLoaded = true
                    }
                }

                Log.d("MainActivity", "Recomposing: isSessionLoaded=$isSessionLoaded, sessionToken='$sessionToken'")

                if (!isSessionLoaded) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    if (sessionToken == null) {
                        val authViewModel = viewModelFactory {
                            AuthViewModel(apiClient, preferencesManager)
                        }
                        AuthScreen(
                            viewModel = authViewModel,
                            onAuthSuccess = {
                                // Handled automatically by the sessionToken flow observer
                            }
                        )
                    } else {
                        val navController = rememberNavController()
                        NavHost(
                            navController = navController,
                            startDestination = Screen.ConversationList.route
                        ) {
                            composable(Screen.ConversationList.route) {
                                val conversationsViewModel = viewModelFactory {
                                    ConversationsViewModel(chatRepository)
                                }
                                ConversationListScreen(
                                    viewModel = conversationsViewModel,
                                    onConversationClick = { convId ->
                                        navController.navigate(Screen.Chat.createRoute(convId))
                                    },
                                    onSettingsClick = {
                                        navController.navigate(Screen.Settings.route)
                                    },
                                    onAddContactClick = {
                                        navController.navigate(Screen.ContactDiscovery.route)
                                    }
                                )
                            }

                            composable(Screen.Chat.route) { backStackEntry ->
                                val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
                                val chatViewModel = viewModelFactory {
                                    ChatViewModel(conversationId, chatRepository, preferencesManager)
                                }
                                ChatScreen(
                                    viewModel = chatViewModel,
                                    onBackClick = {
                                        navController.popBackStack()
                                    }
                                )
                            }

                            composable(Screen.Settings.route) {
                                val settingsViewModel = viewModelFactory {
                                    SettingsViewModel(apiClient, preferencesManager)
                                }
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBackClick = {
                                        navController.popBackStack()
                                    },
                                    onProfileClick = {
                                        navController.navigate(Screen.Profile.route)
                                    },
                                    onLogoutSuccess = {
                                        // Handled automatically by the sessionToken flow observer
                                    }
                                )
                            }

                            composable(Screen.Profile.route) {
                                val profileViewModel = viewModelFactory {
                                    ProfileViewModel(apiClient, preferencesManager)
                                }
                                ProfileScreen(
                                    viewModel = profileViewModel,
                                    onBackClick = {
                                        navController.popBackStack()
                                    }
                                )
                            }

                            composable(Screen.ContactDiscovery.route) {
                                val contactDiscoveryViewModel = viewModelFactory {
                                    com.whisprtext.app.ui.viewmodel.ContactDiscoveryViewModel(chatRepository)
                                }
                                com.whisprtext.app.ui.screen.ContactDiscoveryScreen(
                                    viewModel = contactDiscoveryViewModel,
                                    onBackClick = {
                                        navController.popBackStack()
                                    },
                                    onChatCreated = { convId ->
                                        navController.popBackStack()
                                        navController.navigate(Screen.Chat.createRoute(convId))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
inline fun <reified VM : ViewModel> viewModelFactory(crossinline f: () -> VM): VM {
    return androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return f() as T
            }
        }
    )
}