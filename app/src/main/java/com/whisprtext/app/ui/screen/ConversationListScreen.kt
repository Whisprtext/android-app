package com.whisprtext.app.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.ui.component.InitialsAvatar
import androidx.compose.ui.text.font.FontWeight
import com.whisprtext.app.ui.theme.DynaPuffFontFamily
import com.whisprtext.app.ui.viewmodel.ConversationsViewModel

import com.whisprtext.app.util.ContactHelper
import kotlinx.coroutines.launch

enum class HomeTab(val title: String, val icon: ImageVector) {
    Chat("Chat", Icons.Rounded.ChatBubble),
    Community("Community", Icons.Rounded.Groups),
    Calls("Calls", Icons.Rounded.Phone),
    AI("AI", Icons.Rounded.AutoAwesome),
    Tracking("Tracking", Icons.Rounded.LocationOn)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    val pagerState = rememberPagerState(pageCount = { HomeTab.entries.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSelectionMode) {
                    TopAppBar(
                        title = { Text("${selectedConversationIds.size} selected") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        ),
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
                        title = {
                            Text(
                                when (pagerState.currentPage) {
                                    0 -> "WhisprText"
                                    1 -> "Community"
                                    2 -> "Calls"
                                    3 -> "Whispr AI"
                                    4 -> "Tracking"
                                    else -> "WhisprText"
                                },
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = DynaPuffFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        ),
                        actions = {
                            if (pagerState.currentPage == 0) {
                                IconButton(onClick = { viewModel.sync() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                }
                            }
                            IconButton(onClick = onProfileClick) {
                                InitialsAvatar(
                                    id = uiState.username ?: "Me",
                                    avatarUrl = uiState.avatarUrl,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    modifier = Modifier
                        .height(120.dp)
                        .padding(top = 12.dp, bottom = 12.dp)
                ) {
                    HomeTab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (pagerState.currentPage == 0) {
                FloatingActionButton(
                    onClick = onAddContactClick,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Chat")
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                0 -> {
                    Box(modifier = Modifier.fillMaxSize()) {
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
                1 -> PlaceholderTabContent("Community", Icons.Rounded.Groups)
                2 -> PlaceholderTabContent("Calls", Icons.Rounded.Phone)
                3 -> PlaceholderTabContent("Whispr AI", Icons.Rounded.AutoAwesome)
                4 -> PlaceholderTabContent("Tracking", Icons.Rounded.LocationOn)
            }
        }
    }
}

@Composable
fun PlaceholderTabContent(title: String, icon: ImageVector) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "$title Screen Coming Soon",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                contactsMap[normalizedPhone] ?: conversation.title ?: conversation.username ?: "Chat"
            } else {
                conversation.title ?: conversation.username ?: "Chat"
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
                val avatarId = conversation.username ?: conversation.title ?: "Chat"
                InitialsAvatar(
                    id = avatarId,
                    avatarUrl = conversation.avatarUrl,
                    modifier = Modifier.size(40.dp)
                )
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
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
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
