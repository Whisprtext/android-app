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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.ui.viewmodel.ChatViewModel
import com.whisprtext.app.util.ContactHelper
import com.whisprtext.app.util.MarkdownParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit,
    onProfileClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    var textMessage by remember { mutableStateOf(TextFieldValue("")) }
    var messageToDelete by remember { mutableStateOf<MessageEntity?>(null) }

    val context = LocalContext.current
    val contactsMap = remember(context) { ContactHelper.getContactsMap(context) }
    val displayTitle = remember(uiState.conversation, contactsMap) {
        val conversation = uiState.conversation
        if (conversation != null) {
            if (conversation.type == "direct") {
                val normalizedPhone = conversation.phoneNumber?.let { ContactHelper.normalizePhone(it) }
                if (normalizedPhone != null && contactsMap.containsKey(normalizedPhone)) {
                    contactsMap[normalizedPhone] ?: conversation.username ?: "Chat"
                } else {
                    conversation.username ?: "Chat"
                }
            } else {
                conversation.title ?: "Chat"
            }
        } else {
            "Chat"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val targetUsername = uiState.conversation?.username
                    val isDirect = uiState.conversation?.type == "direct"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isDirect && !targetUsername.isNullOrEmpty()) {
                                if (targetUsername != null) {
                                    onProfileClick(targetUsername)
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDirect) {
                            InitialsAvatar(
                                id = targetUsername ?: displayTitle,
                                avatarUrl = uiState.otherUser?.avatarUrl,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
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
                                isGroupFooter = isGroupFooter,
                                onLongClick = { messageToDelete = message }
                            )
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

            val markdownTransformation = remember {
                VisualTransformation { text ->
                    val annotatedString = MarkdownParser.parse(text.text, hideMarkers = false)
                    TransformedText(annotatedString, OffsetMapping.Identity)
                }
            }

            fun applyFormatting(marker: String) {
                val selection = textMessage.selection
                val text = textMessage.text
                val newText = StringBuilder()
                val newSelectionStart: Int
                val newSelectionEnd: Int

                if (selection.collapsed) {
                    val cursor = selection.start
                    newText.append(text.substring(0, cursor))
                    newText.append(marker)
                    newText.append(marker)
                    newText.append(text.substring(cursor))
                    newSelectionStart = cursor + marker.length
                    newSelectionEnd = cursor + marker.length
                } else {
                    val start = selection.min
                    val end = selection.max
                    newText.append(text.substring(0, start))
                    newText.append(marker)
                    newText.append(text.substring(start, end))
                    newText.append(marker)
                    newText.append(text.substring(end))
                    newSelectionStart = start
                    newSelectionEnd = end + marker.length * 2
                }
                textMessage = TextFieldValue(
                    text = newText.toString(),
                    selection = androidx.compose.ui.text.TextRange(newSelectionStart, newSelectionEnd)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { applyFormatting("*") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text(
                            text = "B",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { applyFormatting("_") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text(
                            text = "I",
                            fontStyle = FontStyle.Italic,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { applyFormatting("~") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text(
                            text = "S",
                            textDecoration = TextDecoration.LineThrough,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { applyFormatting("`") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text(
                            text = "</>",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = textMessage,
                        onValueChange = { textMessage = it },
                        placeholder = { Text("Enter message...") },
                        modifier = Modifier
                            .weight(1f),
                        singleLine = false,
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp),
                        visualTransformation = markdownTransformation
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val isSendEnabled = textMessage.text.isNotBlank() && !uiState.isLoading
                    IconButton(
                        onClick = {
                            if (isSendEnabled) {
                                viewModel.sendMessage(textMessage.text)
                                textMessage = TextFieldValue("")
                            }
                        },
                        enabled = isSendEnabled,
                        modifier = Modifier.padding(bottom = 4.dp),
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

    if (messageToDelete != null) {
        val msg = messageToDelete!!
        val isSelf = msg.senderId == currentUserId
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("Delete message?") },
            text = { Text("Do you want to delete this message?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(msg.id, forEveryone = false)
                    messageToDelete = null
                }) {
                    Text("Delete for Me")
                }
            },
            dismissButton = {
                Row {
                    if (isSelf) {
                        TextButton(onClick = {
                            viewModel.deleteMessage(msg.id, forEveryone = true)
                            messageToDelete = null
                        }) {
                            Text("Delete for Everyone", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = { messageToDelete = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isSelf: Boolean,
    isGroupHeader: Boolean,
    isGroupFooter: Boolean,
    onLongClick: () -> Unit
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
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(bubbleShape)
                .background(bubbleColor)
                .combinedClickable(
                    onLongClick = onLongClick,
                    onClick = {}
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

            Layout(
                content = {
                    Text(
                        text = MarkdownParser.parse(message.encryptedContent, hideMarkers = true),
                        color = textColor,
                        fontSize = 16.sp,
                        onTextLayout = { textLayoutResult = it }
                    )
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
                            val statusText = when (message.syncStatus) {
                                "pending" -> "🕒"
                                "failed" -> "✕"
                                "sent" -> "✓"
                                "delivered" -> "✓✓"
                                "read" -> "✓✓✓"
                                else -> "✓"
                            }
                            val statusColor = when (message.syncStatus) {
                                "failed" -> androidx.compose.ui.graphics.Color.Red
                                "read" -> androidx.compose.ui.graphics.Color(0xFF34B7F1)
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
            ) { measurables, constraints ->
                val textPlaceable = measurables[0].measure(constraints)
                val timePlaceable = measurables[1].measure(constraints.copy(minWidth = 0))

                val parentWidth = constraints.maxWidth

                // Retrieve text layout details
                val layoutResult = textLayoutResult
                val lineCount = layoutResult?.lineCount ?: 0
                
                // If we have text layout info, we can determine the last line width
                val lastLineRight = if (lineCount > 0) layoutResult?.getLineRight(lineCount - 1) ?: 0f else 0f
                val lastLineLeft = if (lineCount > 0) layoutResult?.getLineLeft(lineCount - 1) ?: 0f else 0f
                val lastLineWidth = lastLineRight - lastLineLeft

                val spacingPx = (8 * density).toInt()
                
                // We check if it fits on the last line.
                // It fits if the last line width + spacing + timestamp width is <= parentWidth
                val fitsOnLastLine = lineCount > 0 && (lastLineWidth + spacingPx + timePlaceable.width <= parentWidth)

                // Calculate height
                val layoutHeight = if (fitsOnLastLine) {
                    maxOf(textPlaceable.height, timePlaceable.height)
                } else {
                    textPlaceable.height + timePlaceable.height
                }

                layout(parentWidth, layoutHeight) {
                    // Place the text at the top-left
                    textPlaceable.placeRelative(0, 0)

                    // Place the timestamp at the bottom-right of the layout
                    if (fitsOnLastLine) {
                        timePlaceable.placeRelative(
                            x = parentWidth - timePlaceable.width,
                            y = layoutHeight - timePlaceable.height
                        )
                    } else {
                        timePlaceable.placeRelative(
                            x = parentWidth - timePlaceable.width,
                            y = textPlaceable.height
                        )
                    }
                }
            }
        }
    }
}
