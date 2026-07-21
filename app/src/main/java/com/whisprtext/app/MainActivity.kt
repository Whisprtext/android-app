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
import com.whisprtext.app.ui.theme.Motion
import android.graphics.RuntimeShader
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ShaderBrush
import kotlinx.coroutines.delay
import androidx.lifecycle.ViewModel
import com.whisprtext.app.util.rememberDebouncedNavigator
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
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.RenderEffect

import kotlinx.coroutines.flow.first
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val SPLASH_SHADER_SRC = """
            uniform float2 size;
            uniform float time;
            
            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / size;
                float3 color1 = float3(0.1, 0.1, 0.2); // Darker base
                float3 color2 = float3(0.2, 0.1, 0.3); // Purple tint
                
                float wave = 0.5 + 0.5 * sin(uv.x * 2.0 + uv.y * 3.0 + time);
                float3 finalColor = mix(color1, color2, wave);
                
                return half4(finalColor, 1.0);
            }
        """
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        val app = application as? WhisprTextApp
        app?.webSocketManager?.isAppInForeground = true
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
        val app = application as? WhisprTextApp
        app?.webSocketManager?.isAppInForeground = false
        app?.chatRepository?.isAppInForeground = false
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
        val contactRepository = app.contactRepository
        
        app.webSocketManager.markStartupComplete()

        setContent {
            val appearanceSettings by preferencesManager.appearanceSettings.collectAsState(initial = com.whisprtext.app.data.model.AppearanceSettings())
            
            WhisprtextTheme(appearanceSettings = appearanceSettings) {
                var showSplash by remember { mutableStateOf(true) }
                var isSessionLoaded by remember { mutableStateOf(false) }
                var sessionToken by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(isSessionLoaded) {
                    if (isSessionLoaded) {
                        // Reduce splash delay: wait for session and a small warmup period
                        // instead of a hardcoded 2-second block.
                        delay(600)
                        showSplash = false
                    }
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

                AnimatedContent(
                    targetState = showSplash || !isSessionLoaded,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(600)) togetherWith
                                fadeOut(animationSpec = tween(600))
                    },
                    label = "AppContentTransition"
                ) { targetShowSplash ->
                    if (targetShowSplash) {
                        // Hidden warmup to trigger Compose compilation and shader warmup
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            LaunchedEffect(Unit) {
                                try {
                                    RuntimeShader(SPLASH_SHADER_SRC)
                                } catch (e: Exception) {
                                    // Ignore warmup errors
                                }
                            }
                        }

                        val infiniteTransition = rememberInfiniteTransition(label = "SplashGradientTransition")
                        val time by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 2f * Math.PI.toFloat(),
                            animationSpec = infiniteRepeatable(
                                animation = tween(10000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "SplashGradientTime"
                        )

                        val splashShader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            remember {
                                try { RuntimeShader(SPLASH_SHADER_SRC) } catch (e: Exception) { null }
                            }
                        } else null

                        val gradientModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && splashShader != null) {
                            Modifier.drawWithCache {
                                splashShader.setFloatUniform("size", size.width, size.height)
                                splashShader.setFloatUniform("time", time)
                                val brush = ShaderBrush(splashShader)
                                onDrawBehind {
                                    drawRect(brush)
                                }
                            }
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.background)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(gradientModifier),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimationWarmup()
                            // Use standard Image for the very first frame to avoid Coil init overhead
                            Image(
                                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                contentDescription = "WhisprText Logo",
                                modifier = Modifier
                                    .size(180.dp)
                                    .graphicsLayer {
                                        val scale = 0.9f + 0.1f * Math.sin(time.toDouble() * 2.0).toFloat()
                                        scaleX = scale
                                        scaleY = scale
                                    }
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
                            val debouncedNavigator = rememberDebouncedNavigator(navController)

                            LaunchedEffect(intent) {
                                val convId = intent?.getStringExtra("conversationId")
                                if (!convId.isNullOrEmpty()) {
                                    debouncedNavigator.navigate(Screen.Chat.createRoute(convId)) {
                                        popUpTo(Screen.ConversationList.route)
                                    }
                                    intent?.removeExtra("conversationId")
                                }
                            }

                            NavHost(
                                navController = navController,
                                startDestination = Screen.ConversationList.route,
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                                enterTransition = {
                                    fadeIn(animationSpec = tween(200, easing = Motion.StandardEasing))
                                },
                                exitTransition = {
                                    fadeOut(animationSpec = tween(200, easing = Motion.StandardEasing))
                                },
                                popEnterTransition = {
                                    fadeIn(animationSpec = tween(200, easing = Motion.StandardEasing))
                                },
                                popExitTransition = {
                                    fadeOut(animationSpec = tween(200, easing = Motion.StandardEasing))
                                }
                            ) {
                                composable(Screen.ConversationList.route) {
                                    val conversationsViewModel = viewModelFactory {
                                        ConversationsViewModel(chatRepository, contactRepository, preferencesManager)
                                    }
                                    ConversationListScreen(
                                        viewModel = conversationsViewModel,
                                        onConversationClick = { convId ->
                                            debouncedNavigator.navigate(Screen.Chat.createRoute(convId))
                                        },
                                        onProfileClick = { username ->
                                            debouncedNavigator.navigate(Screen.Profile.createRoute(username))
                                        },
                                        onAddContactClick = {
                                            debouncedNavigator.navigate(Screen.ContactDiscovery.route)
                                        }
                                    )
                                }

                                composable(Screen.Chat.route) { backStackEntry ->
                                    val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
                                    val chatViewModel = viewModelFactory {
                                        ChatViewModel(conversationId, chatRepository, contactRepository, preferencesManager)
                                    }
                                    ChatScreen(
                                        viewModel = chatViewModel,
                                        onBackClick = {
                                            debouncedNavigator.popBackStack()
                                        },
                                        onProfileClick = { targetUsername ->
                                            debouncedNavigator.navigate(Screen.Profile.createRoute(targetUsername))
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
                                            debouncedNavigator.popBackStack()
                                        },
                                        onPrivacyClick = {
                                            debouncedNavigator.navigate(Screen.Privacy.route)
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
                                    )
                                ) { backStackEntry ->
                                    val username = backStackEntry.arguments?.getString("username")
                                    val profileViewModel = viewModelFactory {
                                        ProfileViewModel(
                                            apiClient,
                                            preferencesManager,
                                            chatRepository,
                                            username,
                                            applicationContext
                                        )
                                    }
                                    ProfileScreen(
                                        viewModel = profileViewModel,
                                        onBackClick = {
                                            debouncedNavigator.popBackStack()
                                        },
                                        onSettingsClick = {
                                            debouncedNavigator.navigate(Screen.Settings.route)
                                        },
                                        onPrivacyClick = {
                                            debouncedNavigator.navigate(Screen.Privacy.route)
                                        },
                                        onAppearanceClick = {
                                            debouncedNavigator.navigate(Screen.Appearance.route)
                                        },
                                        isOwnProfile = username == null
                                    )
                                }

                                composable(Screen.Privacy.route) {
                                    val profileViewModel = viewModelFactory {
                                        ProfileViewModel(
                                            apiClient,
                                            preferencesManager,
                                            chatRepository,
                                            appContext = applicationContext
                                        )
                                    }
                                    PrivacyScreen(
                                        viewModel = profileViewModel,
                                        onBackClick = {
                                            debouncedNavigator.popBackStack()
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
                                            debouncedNavigator.popBackStack()
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
                                            debouncedNavigator.popBackStack()
                                        },
                                        onChatCreated = { convId ->
                                            debouncedNavigator.popBackStack()
                                            debouncedNavigator.navigate(Screen.Chat.createRoute(convId))
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

/**
 * A hidden component that triggers basic Compose animations and layouts
 * to warm up the graphics stack and compiler during the splash screen.
 */
@Composable
private fun AnimationWarmup() {
    var start by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        start = true
    }

    // Almost invisible box to trigger drawing and animations
    Box(
        modifier = Modifier
            .size(1.dp)
            .graphicsLayer { alpha = 0.01f }
    ) {
        AnimatedVisibility(
            visible = start,
            enter = fadeIn(tween(100)) + expandIn(tween(100)),
            exit = fadeOut(tween(100)) + shrinkOut(tween(100))
        ) {
            Box(modifier = Modifier.size(1.dp).background(Color.White))
        }

        val alpha by animateFloatAsState(
            targetValue = if (start) 1f else 0f,
            animationSpec = tween(100),
            label = "WarmupAlpha"
        )
        
        val dpValue by animateDpAsState(
            targetValue = if (start) 320.dp else 0.dp,
            animationSpec = tween(100),
            label = "WarmupDp"
        )
        
        Box(
            modifier = Modifier
                .size(1.dp)
                .height(dpValue)
                .graphicsLayer { this.alpha = alpha }
                .background(Color.White)
        )

        // Warm up transition similar to ChatScreen header
        val transition = updateTransition(targetState = start, label = "WarmupTransition")
        val transitionDp by transition.animateDp(label = "WarmupTransitionDp") { if (it) 320.dp else 0.dp }
        val transitionFloat by transition.animateFloat(label = "WarmupTransitionFloat") { if (it) 1f else 0f }
        
        Box(
            modifier = Modifier
                .size(1.dp)
                .height(transitionDp)
                .graphicsLayer { this.alpha = transitionFloat }
        )
    }
}
