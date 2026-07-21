package com.whisprtext.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.whisprtext.app.ui.theme.AppearancePresets
import com.whisprtext.app.ui.theme.WhisprTheme

@Preview(showBackground = true, backgroundColor = 0xFFF0F0F0)
@Composable
fun IncomingBubblePreview() {
    val theme = AppearancePresets.themes.first()
    Column(modifier = Modifier.padding(20.dp)) {
        ChatBubble(
            content = AnnotatedString("Hello! This is an incoming message."),
            time = "10:00 AM",
            isSelf = false,
            theme = theme,
            isDark = false,
            showBubbles = true
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0F0F0)
@Composable
fun OutgoingBubblePreview() {
    val theme = AppearancePresets.themes.first()
    Column(modifier = Modifier.padding(20.dp)) {
        ChatBubble(
            content = AnnotatedString("Hi! This is an outgoing message."),
            time = "10:01 AM",
            isSelf = true,
            theme = theme,
            isDark = false,
            showBubbles = true
        )
    }
}
