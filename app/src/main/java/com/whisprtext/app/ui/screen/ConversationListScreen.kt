package com.whisprtext.app.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.ui.viewmodel.ConversationsViewModel
import com.whisprtext.app.util.ContactHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    viewModel: ConversationsViewModel,
    onConversationClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onAddContactClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val contactsMap = remember(context) { ContactHelper.getContactsMap(context) }

    var selectedConversationIds by remember { mutableStateOf(emptySet<String>()) }
    val isSelectionMode = selectedConversationIds.isNotEmpty()
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedConversationIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedConversationIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.deleteConversations(selectedConversationIds.toList())
                            selectedConversationIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete all chats") },
                                onClick = {
                                    showMenu = false
                                    viewModel.deleteAllConversations()
                                    selectedConversationIds = emptySet()
                                }
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Chats") },
                    actions = {
                        IconButton(onClick = { viewModel.sync() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = onProfileClick) {
                            InitialsAvatar(
                                id = uiState.username ?: "Me",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddContactClick) {
                Icon(Icons.Default.Add, contentDescription = "New Chat")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.isLoading && uiState.conversations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.conversations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No active conversations. Start a new one!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.conversations) { conversation ->
                        val isSelected = selectedConversationIds.contains(conversation.id)
                        ConversationItem(
                            conversation = conversation,
                            contactsMap = contactsMap,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onLongClick = {
                                if (!isSelectionMode) {
                                    selectedConversationIds = setOf(conversation.id)
                                }
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    selectedConversationIds = if (isSelected) {
                                        selectedConversationIds - conversation.id
                                    } else {
                                        selectedConversationIds + conversation.id
                                    }
                                } else {
                                    onConversationClick(conversation.id)
                                }
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
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
    }
}

@Composable
fun InitialsAvatar(
    id: String,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null
) {
    val initials = id.take(2).uppercase()
    val colors = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8), Color(0xFF9575CD),
        Color(0xFF7986CB), Color(0xFF64B5F6), Color(0xFF4FC3F7), Color(0xFF4DB6AC),
        Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFFB74D), Color(0xFFFF8A65)
    )
    val colorIndex = Math.abs(id.hashCode()) % colors.size
    val backgroundColor = colors[colorIndex]

    var isError by remember(avatarUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(40.dp)
            .background(backgroundColor, shape = CircleShape)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank() && !isError) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = id,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { isError = true }
            )
        } else {
            Text(
                text = initials,
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: ConversationEntity,
    contactsMap: Map<String, String>,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val displayName = remember(conversation, contactsMap) {
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
    }

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }

    ListItem(
        leadingContent = {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
            } else {
                InitialsAvatar(id = displayName)
            }
        },
        headlineContent = {
            Text(displayName)
        },
        supportingContent = {
            Text(
                text = conversation.lastMessageText ?: "No messages yet",
                maxLines = 1,
                fontSize = 14.sp
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val timeStr = formatTime(conversation.lastMessageTime)
                Text(timeStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (conversation.unreadCount > 0 && !isSelectionMode) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(conversation.unreadCount.toString())
                    }
                }
            }
        },
        modifier = Modifier
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    )
}

fun formatTime(timestamp: Long?): String {
    if (timestamp == null || timestamp == 0L) return ""
    return try {
        val instant = java.time.Instant.ofEpochMilli(timestamp)
        val localDateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
        val now = java.time.LocalDateTime.now()
        if (localDateTime.toLocalDate() == now.toLocalDate()) {
            localDateTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
        } else if (localDateTime.toLocalDate() == now.minusDays(1).toLocalDate()) {
            "Yesterday"
        } else {
            localDateTime.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
        }
    } catch (e: Exception) {
        ""
    }
}
