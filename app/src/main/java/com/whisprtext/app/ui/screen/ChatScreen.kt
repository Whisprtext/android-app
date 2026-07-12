package com.whisprtext.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    var textMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat: " + viewModel.conversationId.take(8)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (uiState.isLoading && uiState.messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        reverseLayout = true
                    ) {
                        itemsIndexed(uiState.messages) { index, message ->
                            val isSelf = message.senderId == currentUserId

                            val isSameSenderAsNext = index < uiState.messages.size - 1 && 
                                    uiState.messages[index].senderId == uiState.messages[index + 1].senderId
                            val isWithinTimeAsNext = index < uiState.messages.size - 1 && 
                                    Math.abs(uiState.messages[index].createdAt - uiState.messages[index + 1].createdAt) < 300_000

                            val isSameSenderAsPrev = index > 0 && 
                                    uiState.messages[index].senderId == uiState.messages[index - 1].senderId
                            val isWithinTimeAsPrev = index > 0 && 
                                    Math.abs(uiState.messages[index].createdAt - uiState.messages[index - 1].createdAt) < 300_000

                            val isGroupHeader = !(isSameSenderAsNext && isWithinTimeAsNext)
                            val isGroupFooter = !(isSameSenderAsPrev && isWithinTimeAsPrev)

                            MessageBubble(
                                message = message,
                                isSelf = isSelf,
                                isGroupHeader = isGroupHeader,
                                isGroupFooter = isGroupFooter
                            )
                        }

                        if (uiState.messages.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.isLoadingOlder) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    } else {
                                        TextButton(onClick = { viewModel.loadOlderMessages() }) {
                                            Text("Load Older Messages", fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (uiState.error != null) {
                    Snackbar(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.sync() }) {
                                Text("Retry", color = MaterialTheme.colorScheme.inversePrimary)
                            }
                        }
                    ) {
                        Text(uiState.error ?: "")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textMessage,
                    onValueChange = { textMessage = it },
                    placeholder = { Text("Enter message...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                val isSendEnabled = textMessage.isNotBlank() && !uiState.isLoading
                IconButton(
                    onClick = {
                        if (isSendEnabled) {
                            viewModel.sendMessage(textMessage)
                            textMessage = ""
                        }
                    },
                    enabled = isSendEnabled,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    isSelf: Boolean,
    isGroupHeader: Boolean,
    isGroupFooter: Boolean
) {
    val bubbleColor = if (isSelf) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSelf) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (isSelf) Alignment.End else Alignment.Start

    val topStartRadius = 12.dp
    val bottomStartRadius = 12.dp
    val topEndRadius = 12.dp
    val bottomEndRadius = 12.dp

    val bubbleShape = RoundedCornerShape(
        topStart = if (!isSelf && !isGroupHeader) 4.dp else topStartRadius,
        topEnd = if (isSelf && !isGroupHeader) 4.dp else topEndRadius,
        bottomStart = if (!isSelf && !isGroupFooter) 4.dp else bottomStartRadius,
        bottomEnd = if (isSelf && !isGroupFooter) 4.dp else bottomEndRadius
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalAlignment = alignment
    ) {
        if (!isSelf && isGroupHeader) {
            Text(
                text = "Sender: " + message.senderId.take(4),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                Text(text = message.encryptedContent, color = textColor, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val timeInstant = java.time.Instant.ofEpochMilli(message.createdAt)
                    val timeStr = java.time.LocalDateTime.ofInstant(timeInstant, java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                    Text(
                        text = timeStr,
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.6f)
                    )
                    if (isSelf) {
                        Text(
                            text = when (message.syncStatus) {
                                "sending" -> "●"
                                "failed" -> "✕"
                                else -> "✓"
                            },
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
