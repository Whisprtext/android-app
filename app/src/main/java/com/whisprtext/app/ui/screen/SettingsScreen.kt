package com.whisprtext.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisprtext.app.ui.viewmodel.SettingsViewModel

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onLogoutSuccess: () -> Unit
) {
    val username by viewModel.username.collectAsState()
    val userId by viewModel.userId.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.updateStatus.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val isDark = isSystemInDarkTheme()

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
                border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
            ) {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("User Profile Summary", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Username: $username", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("User ID: $userId", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Privacy Option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPrivacyClick() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Text("Privacy", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            // Translation Settings Card
            val isTranslationEnabled by viewModel.isTranslationEnabled.collectAsState(initial = false)
            val preferredTargetLanguage by viewModel.preferredTargetLanguage.collectAsState(initial = "eng_Latn")
            val allowMobileDataDownload by viewModel.allowMobileDataDownload.collectAsState(initial = false)
            val showOriginalBelowTranslation by viewModel.showOriginalBelowTranslation.collectAsState(initial = true)

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Local Translation", style = MaterialTheme.typography.titleMedium)
                            Text("100% on-device local model", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isTranslationEnabled,
                            onCheckedChange = { viewModel.setTranslationEnabled(it) }
                        )
                    }

                    if (isTranslationEnabled) {
                        HorizontalDivider()
                        
                        var targetLangMenuExpanded by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { targetLangMenuExpanded = true }
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Target Language", style = MaterialTheme.typography.bodyMedium)
                                    Text("Select target translation language (202 languages)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val targetDisplayName = com.whisprtext.app.translation.LanguageCodeMapper.getDisplayNameForNllbCode(preferredTargetLanguage)
                                Text(
                                    targetDisplayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            DropdownMenu(
                                expanded = targetLangMenuExpanded,
                                onDismissRequest = { targetLangMenuExpanded = false },
                                modifier = Modifier.heightIn(max = 400.dp)
                            ) {
                                com.whisprtext.app.translation.LanguageCodeMapper.SUPPORTED_LANGUAGES.forEach { (displayName, code) ->
                                    DropdownMenuItem(
                                        text = { Text(displayName) },
                                        onClick = {
                                            viewModel.setPreferredTargetLanguage(code)
                                            targetLangMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Allow Mobile Data Download", style = MaterialTheme.typography.bodyMedium)
                                Text("Wi-Fi recommended for ~1.8GB model", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = allowMobileDataDownload,
                                onCheckedChange = { viewModel.setAllowMobileDataDownload(it) }
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Show Original Below Translation", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(
                                checked = showOriginalBelowTranslation,
                                onCheckedChange = { viewModel.setShowOriginalBelowTranslation(it) }
                            )
                        }

                        HorizontalDivider()

                        // Configurable Manifest URL for Dev/LAN testing
                        val currentManifestUrl by viewModel.manifestUrl.collectAsState(initial = "")
                        var tempManifestUrl by remember(currentManifestUrl) { mutableStateOf(currentManifestUrl) }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Manifest Server URL (Dev/LAN)", style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(
                                value = tempManifestUrl,
                                onValueChange = {
                                    tempManifestUrl = it
                                    viewModel.setManifestUrl(it)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilterChip(
                                    selected = tempManifestUrl == "http://10.0.2.2:8000/translation-manifest.json",
                                    onClick = {
                                        val url = "http://10.0.2.2:8000/translation-manifest.json"
                                        tempManifestUrl = url
                                        viewModel.setManifestUrl(url)
                                    },
                                    label = { Text("Emulator 10.0.2.2", fontSize = 10.sp) }
                                )
                                FilterChip(
                                    selected = tempManifestUrl == "http://localhost:8000/translation-manifest.json",
                                    onClick = {
                                        val url = "http://localhost:8000/translation-manifest.json"
                                        tempManifestUrl = url
                                        viewModel.setManifestUrl(url)
                                    },
                                    label = { Text("Localhost", fontSize = 10.sp) }
                                )
                                FilterChip(
                                    selected = tempManifestUrl == com.whisprtext.app.BuildConfig.TRANSLATION_MANIFEST_URL,
                                    onClick = {
                                        val defaultUrl = com.whisprtext.app.BuildConfig.TRANSLATION_MANIFEST_URL
                                        tempManifestUrl = defaultUrl
                                        viewModel.setManifestUrl(defaultUrl)
                                    },
                                    label = { Text("Default", fontSize = 10.sp) }
                                )
                            }
                        }

                        HorizontalDivider()

                        val dlState by viewModel.modelDownloadState.collectAsState()
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Translation Model Status", style = MaterialTheme.typography.titleSmall)
                            when (val state = dlState) {
                                is com.whisprtext.app.translation.ModelDownloadState.Installed -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Ready (NLLB 600M INT8)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                            Text("App-private storage (~1.8GB)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        OutlinedButton(onClick = { viewModel.deleteModel() }) {
                                            Text("Delete Model", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                is com.whisprtext.app.translation.ModelDownloadState.Downloading -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Downloading model: ${state.currentFile}", style = MaterialTheme.typography.bodySmall)
                                            Text("${state.progressPercent}%", style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        }
                                        LinearProgressIndicator(
                                            progress = { state.progressPercent / 100f },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                is com.whisprtext.app.translation.ModelDownloadState.Verifying -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Verifying SHA-256: ${state.currentFile}", style = MaterialTheme.typography.bodySmall)
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                is com.whisprtext.app.translation.ModelDownloadState.Checking -> {
                                    Text("Checking model status...", style = MaterialTheme.typography.bodySmall)
                                }
                                is com.whisprtext.app.translation.ModelDownloadState.Failed -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(state.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                        Button(onClick = { viewModel.downloadModel() }) {
                                            Text("Retry Download")
                                        }
                                    }
                                }
                                is com.whisprtext.app.translation.ModelDownloadState.NotDownloaded -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Model Not Installed", style = MaterialTheme.typography.bodyMedium)
                                            Text("Requires ~1.8GB download", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Button(onClick = { viewModel.downloadModel() }) {
                                            Text("Download Model")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.logout(onLogoutSuccess)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Log Out", color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}
