package com.whisprtext.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.whisprtext.app.ui.navigation.Screen
import com.whisprtext.app.ui.screen.AuthScreen
import com.whisprtext.app.ui.screen.ChatScreen
import com.whisprtext.app.ui.screen.ConversationListScreen
import com.whisprtext.app.ui.screen.SettingsScreen
import com.whisprtext.app.ui.screen.ProfileScreen
import com.whisprtext.app.ui.screen.PrivacyScreen
import com.whisprtext.app.ui.screen.AppearanceScreen
import com.whisprtext.app.ui.theme.WhisprtextTheme
import com.whisprtext.app.ui.viewmodel.AuthViewModel
import com.whisprtext.app.ui.viewmodel.ChatViewModel
import com.whisprtext.app.ui.viewmodel.ConversationsViewModel
import com.whisprtext.app.ui.viewmodel.SettingsViewModel
import com.whisprtext.app.ui.viewmodel.ProfileViewModel
import com.whisprtext.app.ui.viewmodel.AppearanceViewModel

import android.content.Intent
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.WindowManager

import kotlinx.coroutines.flow.first
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        val app = application as? WhisprTextApp
        app?.chatRepository?.let { repo ->
            repo.isAppInForeground = true
            repo.activeConversationId?.let { activeId ->
                lifecycleScope.launch {
                    repo.markConversationAsRead(activeId)
                }
            }
            // Flush any pending receipts accumulated while the app was in the background
            // and pull any missed messages / receipt updates from the server.
            lifecycleScope.launch {
                repo.syncReceipts()
                repo.syncDelta()
            }
        }
        // Force WebSocket reconnect when returning to foreground
        app?.webSocketManager?.forceReconnect()
    }

    override fun onStop() {
        super.onStop()
        (application as? WhisprTextApp)?.chatRepository?.isAppInForeground = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request maximum refresh rate for smoothest animations
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val modes = display?.supportedModes
            val maxMode = modes?.maxByOrNull { it.refreshRate }
            if (maxMode != null) {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = maxMode.modeId
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        
        val app = application as WhisprTextApp
        val preferencesManager = app.preferencesManager
        val apiClient = app.apiClient
        val chatRepository = app.chatRepository

        setContent {
            val appearanceSettings by preferencesManager.appearanceSettings.collectAsState(initial = com.whisprtext.app.data.model.AppearanceSettings())
            
            WhisprtextTheme(appearanceSettings = appearanceSettings) {
                var showSplash by remember { mutableStateOf(true) }
                var isSessionLoaded by remember { mutableStateOf(false) }
                var sessionToken by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    delay(2000)
                    showSplash = false
                }

                LaunchedEffect(preferencesManager) {
                    Log.d("MainActivity", "LaunchedEffect: starting preference observation")
                    preferencesManager.sessionToken.collect { token ->
                        Log.d("MainActivity", "LaunchedEffect observed sessionToken: '$token'")
                        sessionToken = token
                        isSessionLoaded = true
                    }
                }

                LaunchedEffect(sessionToken) {
                    if (sessionToken != null) {
                        try {
                            val userId = preferencesManager.userId.first() ?: "unknown"
                            val mockToken = "mock_token_" + userId.take(8)
                            chatRepository.registerPushToken(mockToken)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                Log.d("MainActivity", "Recomposing: showSplash=$showSplash, isSessionLoaded=$isSessionLoaded, sessionToken='$sessionToken'")

                if (showSplash || !isSessionLoaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(R.mipmap.ic_launcher_foreground)
                                .build(),
                            contentDescription = "WhisprText Logo",
                            modifier = Modifier.size(180.dp)
                        )
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

                        LaunchedEffect(intent) {
                            val convId = intent?.getStringExtra("conversationId")
                            if (!convId.isNullOrEmpty()) {
                                navController.navigate(Screen.Chat.createRoute(convId)) {
                                    popUpTo(Screen.ConversationList.route)
                                }
                                intent?.removeExtra("conversationId")
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = Screen.ConversationList.route
                        ) {
                            composable(Screen.ConversationList.route) {
                                val conversationsViewModel = viewModelFactory {
                                    ConversationsViewModel(chatRepository, preferencesManager)
                                }
                                ConversationListScreen(
                                    viewModel = conversationsViewModel,
                                    onConversationClick = { convId ->
                                        navController.navigate(Screen.Chat.createRoute(convId))
                                    },
                                    onProfileClick = {
                                        navController.navigate(Screen.Profile.createRoute())
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
                                    },
                                    onProfileClick = { targetUsername ->
                                        navController.navigate(Screen.Profile.createRoute(targetUsername))
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
                                    onPrivacyClick = {
                                        navController.navigate(Screen.Privacy.route)
                                    },
                                    onLogoutSuccess = {
                                        // Handled automatically by the sessionToken flow observer
                                    }
                                )
                            }

                            composable(
                                route = Screen.Profile.route,
                                arguments = listOf(
                                    navArgument("username") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                ),
                                enterTransition = {
                                    slideInVertically(initialOffsetY = { -it }) + fadeIn()
                                },
                                popExitTransition = {
                                    slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                                }
                            ) { backStackEntry ->
                                val username = backStackEntry.arguments?.getString("username")
                                val profileViewModel = viewModelFactory {
                                    ProfileViewModel(apiClient, preferencesManager, username)
                                }
                                ProfileScreen(
                                    viewModel = profileViewModel,
                                    onBackClick = {
                                        navController.popBackStack()
                                    },
                                    onSettingsClick = {
                                        navController.navigate(Screen.Settings.route)
                                    },
                                    onPrivacyClick = {
                                        navController.navigate(Screen.Privacy.route)
                                    },
                                    onAppearanceClick = {
                                        navController.navigate(Screen.Appearance.route)
                                    },
                                    isOwnProfile = username == null
                                )
                            }

                            composable(Screen.Privacy.route) {
                                val profileViewModel = viewModelFactory {
                                    ProfileViewModel(apiClient, preferencesManager)
                                }
                                PrivacyScreen(
                                    viewModel = profileViewModel,
                                    onBackClick = {
                                        navController.popBackStack()
                                    }
                                )
                            }

                            composable(Screen.Appearance.route) {
                                val appearanceViewModel = viewModelFactory {
                                    AppearanceViewModel(preferencesManager)
                                }
                                AppearanceScreen(
                                    viewModel = appearanceViewModel,
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