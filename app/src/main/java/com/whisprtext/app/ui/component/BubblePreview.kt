package com.whisprtext.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.whisprtext.app.ui.theme.AppearancePresets
import com.whisprtext.app.ui.theme.WhisprTheme

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
