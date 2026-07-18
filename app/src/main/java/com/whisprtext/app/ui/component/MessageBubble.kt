package com.whisprtext.app.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisprtext.app.ui.theme.WhisprTheme
import com.whisprtext.app.ui.theme.InterFontFamily

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    content: AnnotatedString,
    time: String,
    isSelf: Boolean,
    isGroupHeader: Boolean,
    isGroupFooter: Boolean,
    theme: WhisprTheme,
    isDark: Boolean,
    syncStatus: String? = null,
    showTimestamp: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    mediaContent: @Composable (() -> Unit)? = null
) {
    val textColor = if (isDark) Color.White else Color.Black
    val alignment = if (isSelf) Alignment.End else Alignment.Start

    val textStyle = remember(textColor, isSelf) {
        TextStyle(
            color = textColor,
            fontSize = 16.sp,
            fontFamily = InterFontFamily,
            textAlign = if (isSelf) TextAlign.End else TextAlign.Start
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            onLongClick = onLongClick,
                            onClick = {}
                        )
                    } else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalAlignment = alignment
        ) {
            if (mediaContent != null) {
                Box(modifier = Modifier.padding(bottom = 6.dp)) {
                    mediaContent()
                }
            }
            
            val showText = content.text.isNotEmpty() && content.text != "[Media]"
            if (showText) {
                Text(
                    text = content,
                    style = textStyle,
                    modifier = Modifier.wrapContentWidth()
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (showTimestamp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = time,
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.6f)
                    )
                    if (isSelf && syncStatus != null) {
                        val statusText = when (syncStatus) {
                            "pending" -> "🕒"
                            "failed" -> "✕"
                            "sent" -> "✓"
                            "delivered" -> "✓✓"
                            "read" -> "✓✓✓"
                            else -> "✓"
                        }
                        val statusColor = when (syncStatus) {
                            "failed" -> Color.Red
                            "read" -> Color(0xFF34B7F1)
                            else -> textColor.copy(alpha = 0.6f)
                        }
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            color = statusColor
                        )
                    }
                }
            }
        }
    }
}
