package com.whisprtext.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whisprtext.app.ui.component.ChatBubble
import com.whisprtext.app.ui.theme.AppearancePresets
import com.whisprtext.app.ui.theme.WhisprTheme
import com.whisprtext.app.ui.viewmodel.AppearanceViewModel
import com.whisprtext.app.util.MarkdownParser
import com.whisprtext.app.data.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    viewModel: AppearanceViewModel,
    onBackClick: () -> Unit
) {
    val settings by viewModel.appearanceSettings.collectAsState()
    val isDark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val currentTheme = AppearancePresets.getTheme(settings.presetId)

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
                    title = { Text("Appearance") },
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Theme Mode Selection
            Text("App Theme", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = settings.themeMode == ThemeMode.LIGHT,
                    onClick = { viewModel.updateThemeMode(ThemeMode.LIGHT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) {
                    Text("Light")
                }
                SegmentedButton(
                    selected = settings.themeMode == ThemeMode.DARK,
                    onClick = { viewModel.updateThemeMode(ThemeMode.DARK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) {
                    Text("Dark")
                }
                SegmentedButton(
                    selected = settings.themeMode == ThemeMode.SYSTEM,
                    onClick = { viewModel.updateThemeMode(ThemeMode.SYSTEM) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) {
                    Text("System")
                }
            }

            // Demo Chat View
            Text("Preview", style = MaterialTheme.typography.titleMedium)
            DemoChatView(currentTheme, settings.showChatBubbles, isDark)

            // Background Color Selection
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(AppearancePresets.themes) { theme ->
                    ThemeOption(
                        theme = theme,
                        isSelected = settings.presetId == theme.id,
                        isDark = isDark,
                        onClick = { viewModel.updatePreset(theme.id) }
                    )
                }
            }

            // Options
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Chat Bubbles Toggle
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateShowChatBubbles(!settings.showChatBubbles) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Chat Bubbles", style = MaterialTheme.typography.bodyLarge)
                            Text("Show background for messages", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = settings.showChatBubbles,
                            onCheckedChange = { viewModel.updateShowChatBubbles(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DemoChatView(
    theme: WhisprTheme,
    showChatBubbles: Boolean,
    isDark: Boolean
) {
    val backgroundColor = if (isDark) theme.backgroundColorDark else theme.backgroundColorLight
    val gradientColors = if (isDark) theme.gradientColorsDark else theme.gradientColorsLight
    
    val backgroundModifier = if (theme.isGradient) {
        Modifier.background(Brush.verticalGradient(gradientColors))
    } else {
        Modifier.background(backgroundColor)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(backgroundModifier)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatBubble(
                content = MarkdownParser.parse("Hey! How do you like this theme?", hideMarkers = true),
                time = "10:00 AM",
                isSelf = false,
                theme = theme,
                isDark = isDark,
                showBubbles = showChatBubbles
            )
            ChatBubble(
                content = MarkdownParser.parse("It looks amazing! So clean.", hideMarkers = true),
                time = "10:01 AM",
                isSelf = true,
                theme = theme,
                isDark = isDark,
                showBubbles = showChatBubbles,
                syncStatus = "read"
            )
        }
    }
}

@Composable
fun ThemeOption(theme: WhisprTheme, isSelected: Boolean, isDark: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isDark) theme.backgroundColorDark else theme.backgroundColorLight
    val gradientColors = if (isDark) theme.gradientColorsDark else theme.gradientColorsLight
    
    val backgroundModifier = if (theme.isGradient) {
        Modifier.background(Brush.verticalGradient(gradientColors))
    } else {
        Modifier.background(backgroundColor)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(60.dp, 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .then(backgroundModifier)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = theme.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
