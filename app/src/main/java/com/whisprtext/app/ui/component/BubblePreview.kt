package com.whisprtext.app.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.whisprtext.app.ui.theme.AppearancePresets
import com.whisprtext.app.ui.theme.WhisprTheme

@Preview(showBackground = true, backgroundColor = 0xFFF0F0F0)
@Composable
fun MessageAnimationPreview() {
    val theme = AppearancePresets.themes.first()
    var animTrigger by remember { mutableStateOf(0) }

    Column(modifier = Modifier.padding(20.dp)) {
        Text(text = "Animation Test (Click Replay to see pops)")
        Button(onClick = { animTrigger++ }) {
            Text("Replay Animation")
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Keyed by animTrigger to force reset of internal Animatable state for demo purposes
        key(animTrigger) {
            Column {
                ChatBubble(
                    content = AnnotatedString("Hello! This is a short incoming message popping from the tail."),
                    time = "10:00 AM",
                    isSelf = false,
                    theme = theme,
                    isDark = false,
                    shouldAnimate = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                ChatBubble(
                    content = AnnotatedString("And this is a longer outgoing message that should also pop from its tail smoothly. It spans multiple lines to ensure the tail connection remains visual solid throughout the scaling process."),
                    time = "10:01 AM",
                    isSelf = true,
                    theme = theme,
                    isDark = false,
                    shouldAnimate = true
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0F0F0)
@Composable
fun ThemesPreview() {
    Column(modifier = Modifier.padding(20.dp)) {
        AppearancePresets.themes.forEach { theme ->
            Text(text = "Theme: ${theme.name}")
            ChatBubble(
                content = AnnotatedString("Outgoing message in ${theme.name}"),
                time = "12:00 PM",
                isSelf = true,
                theme = theme,
                isDark = false
            )
            Spacer(modifier = Modifier.height(8.dp))
            ChatBubble(
                content = AnnotatedString("Incoming message in ${theme.name}"),
                time = "12:01 PM",
                isSelf = false,
                theme = theme,
                isDark = false
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1B2E)
@Composable
fun DarkThemesPreview() {
    Column(modifier = Modifier.padding(20.dp)) {
        AppearancePresets.themes.forEach { theme ->
            Text(text = "Theme: ${theme.name}", color = Color.White)
            ChatBubble(
                content = AnnotatedString("Outgoing message (Dark)"),
                time = "12:02 PM",
                isSelf = true,
                theme = theme,
                isDark = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            ChatBubble(
                content = AnnotatedString("Incoming message (Dark)"),
                time = "12:03 PM",
                isSelf = false,
                theme = theme,
                isDark = true
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
