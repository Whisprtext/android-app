package com.whisprtext.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.whisprtext.app.ui.component.StatusAvatar
import androidx.compose.ui.text.font.FontWeight
import com.whisprtext.app.ui.theme.DynaPuffFontFamily
import com.whisprtext.app.ui.theme.Motion
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
    onProfileClick: (String?) -> Unit,
    onAddContactClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val contactsMap = uiState.contactsMap

    var selectedConversationIds by remember { mutableStateOf(emptySet<String>()) }
    val isSelectionMode = selectedConversationIds.isNotEmpty()
    var showMenu by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { HomeTab.entries.size })
    val scope = rememberCoroutineScope()

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val conversationClickOnce = remember { { id: String -> onConversationClick(id) } }
    val profileClickOnce = remember { { username: String? -> onProfileClick(username) } }
    val addContactClickOnce = remember { { onAddContactClick() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
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
                    Column(
                        modifier = Modifier.animateContentSize(
                            animationSpec = tween(
                                durationMillis = 400,
                                easing = FastOutSlowInEasing
                            )
                        )
                    ) {
                        TopAppBar(
                            title = {
                                AnimatedContent(
                                    targetState = pagerState.currentPage,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(Motion.ShortDuration2)) togetherWith
                                                fadeOut(animationSpec = tween(Motion.ShortDuration2))
                                    },
                                    label = "TitleTransition"
                                ) { page ->
                                    Text(
                                        when (page) {
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
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent
                            ),
                            actions = {
                                val isChatTab = pagerState.currentPage == 0
                                AnimatedVisibility(
                                    visible = isChatTab,
                                    enter = fadeIn(animationSpec = tween(Motion.ShortDuration2)) + 
                                            scaleIn(animationSpec = tween(Motion.ShortDuration2)),
                                    exit = fadeOut(animationSpec = tween(Motion.ShortDuration2)) + 
                                            scaleOut(animationSpec = tween(Motion.ShortDuration2))
                                ) {
                                    Row {
                                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                            Icon(
                                                imageVector = if (isSearchActive) Icons.Rounded.SearchOff else Icons.Rounded.Search,
                                                contentDescription = "Search"
                                            )
                                        }
                                        IconButton(onClick = { /* TODO: Implement payment screen navigation */ }) {
                                            Icon(Icons.Rounded.CreditCard, contentDescription = "Payments")
                                        }
                                    }
                                }
                                IconButton(onClick = { profileClickOnce(null) }) {
                                    InitialsAvatar(
                                        id = uiState.displayName ?: uiState.username ?: "Me",
                                        avatarUrl = uiState.avatarUrl,
                                        modifier = Modifier.size(32.dp),
                                        gradientStart = uiState.gradientStart?.let { Color(it) },
                                        gradientEnd = uiState.gradientEnd?.let { Color(it) }
                                    )
                                }
                            }
                        )
                        
                        AnimatedVisibility(
                            visible = pagerState.currentPage == 0,
                            enter = expandVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(animationSpec = tween(Motion.MediumDuration2)),
                            exit = shrinkVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeOut(animationSpec = tween(Motion.MediumDuration2))
                        ) {
                            Column {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // First show "Your Story" (me)
                                    item {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.animateItem()
                                        ) {
                                            Box(modifier = Modifier.clickable { profileClickOnce(null) }) {
                                                StatusAvatar(
                                                    avatarUrl = uiState.avatarUrl,
                                                    id = uiState.displayName ?: uiState.username ?: "Me",
                                                    showBorder = false,
                                                    showPlusIcon = true,
                                                    gradientStart = uiState.gradientStart?.let { Color(it) },
                                                    gradientEnd = uiState.gradientEnd?.let { Color(it) }
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "My Status",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Then show contacts' avatars
                                    val statusConversations = uiState.conversations.filter { it.avatarUrl != null || it.title != null }
                                    items(
                                        items = statusConversations,
                                        key = { it.id }
                                    ) { conversation ->
                                        val name = remember(conversation, contactsMap) {
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
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.animateItem()
                                        ) {
                                            Box(modifier = Modifier.clickable { 
                                                if (conversation.type == "direct") profileClickOnce(conversation.username) 
                                            }) {
                                                StatusAvatar(
                                                    avatarUrl = conversation.avatarUrl,
                                                    id = name,
                                                    gradientStart = conversation.gradientStartColor?.let { Color(it) },
                                                    gradientEnd = conversation.gradientEndColor?.let { Color(it) }
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = name.take(8),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                
                                AnimatedVisibility(
                                    visible = isSearchActive,
                                    enter = expandVertically(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ) + fadeIn(animationSpec = tween(Motion.MediumDuration2)),
                                    exit = shrinkVertically(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ) + fadeOut(animationSpec = tween(Motion.MediumDuration2))
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                        placeholder = { Text("Search chats...") },
                                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { searchQuery = "" }) {
                                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(28.dp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
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
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    onClick = addContactClickOnce,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
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
                            val filteredConversations = remember(uiState.conversations, searchQuery) {
                                if (searchQuery.isEmpty()) {
                                    uiState.conversations
                                } else {
                                    uiState.conversations.filter { conversation ->
                                        val name = if (conversation.type == "direct") {
                                            val normalizedPhone = conversation.phoneNumber?.let { ContactHelper.normalizePhone(it) }
                                            if (normalizedPhone != null && contactsMap.containsKey(normalizedPhone)) {
                                                contactsMap[normalizedPhone] ?: conversation.title ?: conversation.username ?: ""
                                            } else {
                                                conversation.title ?: conversation.username ?: ""
                                            }
                                        } else {
                                            conversation.title ?: ""
                                        }
                                        name.contains(searchQuery, ignoreCase = true) ||
                                                (conversation.lastMessageText?.contains(searchQuery, ignoreCase = true) == true)
                                    }
                                }
                            }

                            if (filteredConversations.isEmpty() && searchQuery.isNotEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No chats match \"$searchQuery\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(filteredConversations) { conversation ->
                                        val isSelected = selectedConversationIds.contains(conversation.id)
                                    ConversationItem(
                                        conversation = conversation,
                                        contactsMap = contactsMap,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        onAvatarClick = {
                                            if (conversation.type == "direct") {
                                                profileClickOnce(conversation.username)
                                            }
                                        },
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
                                                conversationClickOnce(conversation.id)
                                            }
                                        }
                                    )
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
    onAvatarClick: () -> Unit,
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
                InitialsAvatar(
                    id = displayName,
                    avatarUrl = conversation.avatarUrl,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onAvatarClick() },
                    gradientStart = conversation.gradientStartColor?.let { Color(it) },
                    gradientEnd = conversation.gradientEndColor?.let { Color(it) }
                )
            }
        },
        headlineContent = {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = DynaPuffFontFamily,
                    fontWeight = FontWeight.SemiBold
                )
            )
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
