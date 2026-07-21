package com.whisprtext.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.text.BasicTextField
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.ui.component.ChatBubble
import com.whisprtext.app.ui.component.InitialsAvatar
import com.whisprtext.app.ui.component.StickerEmojiPickerBottomSheet
import com.whisprtext.app.ui.theme.AppearancePresets
import com.whisprtext.app.ui.theme.WhisprTheme
import com.whisprtext.app.ui.theme.Motion
import com.whisprtext.app.ui.viewmodel.ChatViewModel
import com.whisprtext.app.ui.viewmodel.ChatUiState
import com.whisprtext.app.ui.viewmodel.MessageUiModel
import com.whisprtext.app.util.ContactHelper
import com.whisprtext.app.util.MarkdownParser
import com.whisprtext.app.data.model.ThemeMode
import com.whisprtext.app.data.remote.model.UserDto
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch
import java.io.File
import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit,
    onProfileClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val otherUser = uiState.otherUser
    val contactsMap = uiState.contactsMap
    val currentUserId by viewModel.currentUserId.collectAsState()
    val appearance = uiState.appearanceSettings
    val isDark = when (appearance.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val theme = remember(appearance.presetId) { AppearancePresets.getTheme(appearance.presetId) }

    val context = LocalContext.current

    val listState = rememberLazyListState()
    var isInitialLoad by remember { mutableStateOf(true) }

    // Media picking logic
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val mime = context.contentResolver.getType(it) ?: "image/jpeg"
            viewModel.sendMediaMessage(it.toString(), mime)
        }
    }

    val mediaPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        if (granted) pickerLauncher.launch("*/*")
    }

    fun launchMediaPicker() {
        val allGranted = mediaPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) pickerLauncher.launch("*/*")
        else permissionLauncher.launch(mediaPermissions)
    }

    val backgroundModifier = remember(theme, isDark) {
        val gradientColors = if (isDark) theme.gradientColorsDark else theme.gradientColorsLight
        val backgroundColor = if (isDark) theme.backgroundColorDark else theme.backgroundColorLight
        if (theme.isGradient) {
            Modifier.background(Brush.verticalGradient(gradientColors))
        } else {
            Modifier.background(backgroundColor)
        }
    }

    var textMessage by remember { mutableStateOf(TextFieldValue("")) }
    var messageToDelete by remember { mutableStateOf<MessageEntity?>(null) }
    var isHeaderExpanded by remember { mutableStateOf(false) }
    var showPickerSheet by remember { mutableStateOf(false) }

    val headerHeight by animateDpAsState(
        targetValue = if (isHeaderExpanded) 320.dp else 0.dp,
        animationSpec = tween(Motion.LongDuration3, easing = Motion.EmphasizedDecelerateEasing),
        label = "HeaderHeight"
    )

    val headerAlpha by animateFloatAsState(
        targetValue = if (isHeaderExpanded) 1f else 0f,
        animationSpec = tween(Motion.MediumDuration2),
        label = "HeaderAlpha"
    )

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val displayTitle = remember(uiState.conversation, contactsMap) {
        val conversation = uiState.conversation
        if (conversation != null) {
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
        } else "Chat"
    }

    val markdownTransformation = remember {
        VisualTransformation { text ->
            val annotatedString = MarkdownParser.parse(text.text, hideMarkers = false)
            TransformedText(annotatedString, OffsetMapping.Identity)
        }
    }

    val latestMessageId = remember(uiState.messages) { uiState.messages.firstOrNull()?.message?.id }
    LaunchedEffect(latestMessageId) {
        if (latestMessageId != null) {
            if (isInitialLoad) isInitialLoad = false
            else listState.animateScrollToItem(0)
        }
    }

    // Formatting Helpers
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

    fun toggleListFormatting(listType: String) {
        val text = textMessage.text
        val selection = textMessage.selection
        val lines = text.split('\n')
        val newLines = lines.toMutableList()
        
        val bulletRegex = Regex("""^(\s*)([-*•])\s+""")
        val numberRegex = Regex("""^(\s*)(\d+)\.\s+""")
        val romanRegex = Regex("""^(\s*)([ivxldcmIVXLDCM]+)\.\s+""")

        // Simple single line toggle for now to keep it lean
        val cursor = selection.start
        var currentOffset = 0
        for (idx in lines.indices) {
            val line = lines[idx]
            val end = currentOffset + line.length
            if (cursor in currentOffset..end) {
                val bulletMatch = bulletRegex.find(line)
                val numberMatch = numberRegex.find(line)
                val romanMatch = romanRegex.find(line)

                val content = when {
                    bulletMatch != null -> line.substring(bulletMatch.range.last + 1)
                    numberMatch != null -> line.substring(numberMatch.range.last + 1)
                    romanMatch != null -> line.substring(romanMatch.range.last + 1)
                    else -> line
                }

                val isSame = when(listType) {
                    "bullet" -> bulletMatch != null
                    "number" -> numberMatch != null
                    "roman" -> romanMatch != null
                    else -> false
                }

                if (isSame) newLines[idx] = content
                else {
                    val prefix = when(listType) {
                        "bullet" -> "\t- "
                        "number" -> "\t1. "
                        "roman" -> "\ti. "
                        else -> ""
                    }
                    newLines[idx] = prefix + content
                }
                break
            }
            currentOffset = end + 1
        }
        val newText = newLines.joinToString("\n")
        textMessage = TextFieldValue(newText, androidx.compose.ui.text.TextRange(newText.length))
    }

    fun onTextMessageChange(newValue: TextFieldValue) {
        // Auto-bullet logic omitted for brevity in this refactor, but can be restored
        textMessage = newValue
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { clip = true }
            .then(backgroundModifier)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                ChatTopBar(
                    uiState = uiState,
                    displayTitle = displayTitle,
                    isDark = isDark,
                    isHeaderExpanded = isHeaderExpanded,
                    onBackClick = onBackClick,
                    onHeaderClick = {
                        if (!isHeaderExpanded) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            isHeaderExpanded = true
                        } else isHeaderExpanded = false
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
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { if (isHeaderExpanded) isHeaderExpanded = false }
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = uiState.isLoading && uiState.messages.isEmpty(),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(Motion.MediumDuration2)) togetherWith
                            fadeOut(animationSpec = tween(Motion.MediumDuration2))
                        },
                        label = "ChatContentTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { isLoading ->
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                reverseLayout = true
                            ) {
                                itemsIndexed(
                                    items = uiState.messages,
                                    key = { _, model -> model.message.id },
                                    contentType = { _, _ -> "message" }
                                ) { _, model ->
                                    MessageBubble(
                                        uiModel = model,
                                        theme = theme,
                                        isDark = isDark,
                                        showBubbles = appearance.showChatBubbles,
                                        onLongClick = { messageToDelete = model.message },
                                        modifier = Modifier.animateItem(),
                                        onDownloadMedia = { viewModel.getDecryptedFilePath(it) }
                                    )
                                }
                            }
                        }
                    }

                    ChatHeaderExpansion(
                        headerHeight = headerHeight,
                        headerAlpha = headerAlpha,
                        uiState = uiState,
                        displayTitle = displayTitle,
                        otherUser = otherUser,
                        isDark = isDark,
                        onProfileClick = onProfileClick
                    )
                }

                if (uiState.error != null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                        Snackbar(
                            action = {
                                TextButton(onClick = { viewModel.sync() }) {
                                    Text("Retry", color = MaterialTheme.colorScheme.inversePrimary)
                                }
                            }
                        ) { Text(uiState.error ?: "") }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    shadowElevation = 12.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ChatFormattingBar(
                            onFormattingClick = ::applyFormatting,
                            onListFormattingClick = ::toggleListFormatting
                        )

                        ChatInputBar(
                            textMessage = textMessage,
                            onTextMessageChange = ::onTextMessageChange,
                            markdownTransformation = markdownTransformation,
                            isLoading = uiState.isLoading,
                            onEmojiClick = { showPickerSheet = true },
                            onMediaClick = ::launchMediaPicker,
                            onSendClick = {
                                viewModel.sendMessage(textMessage.text)
                                textMessage = TextFieldValue("")
                            }
                        )
                    }
                }
            }
        }
    }

    if (showPickerSheet) {
        StickerEmojiPickerBottomSheet(
            onDismissRequest = { showPickerSheet = false },
            onEmojiSelect = { emoji ->
                val currentText = textMessage.text
                val newText = currentText + emoji
                textMessage = TextFieldValue(newText, androidx.compose.ui.text.TextRange(newText.length))
            },
            onStickerSelect = { sticker ->
                showPickerSheet = false
                viewModel.sendStickerMessage(sticker.path, sticker.mimeType)
            }
        )
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
                }) { Text("Delete for Me") }
            },
            dismissButton = {
                Row {
                    if (isSelf) {
                        TextButton(onClick = {
                            viewModel.deleteMessage(msg.id, forEveryone = true)
                            messageToDelete = null
                        }) { Text("Delete for Everyone", color = MaterialTheme.colorScheme.error) }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = { messageToDelete = null }) { Text("Cancel") }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    uiState: ChatUiState,
    displayTitle: String,
    isDark: Boolean,
    isHeaderExpanded: Boolean,
    onBackClick: () -> Unit,
    onHeaderClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = if (isHeaderExpanded) RoundedCornerShape(0.dp) else RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        shadowElevation = if (isHeaderExpanded) 0.dp else 4.dp,
        color = MaterialTheme.colorScheme.surface,
        border = if (isDark && !isHeaderExpanded) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            val isDirect = uiState.conversation?.type == "direct"
            val targetUsername = uiState.conversation?.username

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = isDirect && !targetUsername.isNullOrEmpty()) { onHeaderClick() }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isHeaderExpanded) {
                    if (isDirect) {
                        InitialsAvatar(
                            id = displayTitle,
                            avatarUrl = uiState.otherUser?.avatarUrl,
                            modifier = Modifier.size(36.dp),
                            fontSize = 16.sp,
                            gradientStart = uiState.conversation?.gradientStartColor?.let { Color(it) },
                            gradientEnd = uiState.conversation?.gradientEndColor?.let { Color(it) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column {
                        Text(text = displayTitle, style = MaterialTheme.typography.titleMedium)
                        if (isDirect && uiState.otherUser?.bio?.isNotEmpty() == true) {
                            Text(
                                text = uiState.otherUser.bio,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(text = displayTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { }) { Icon(Icons.Default.Phone, contentDescription = "Voice Call") }
                IconButton(onClick = { }) { Icon(Icons.Default.Videocam, contentDescription = "Video Call") }
                IconButton(onClick = { }) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
            }
        }
    }
}

@Composable
fun ChatHeaderExpansion(
    headerHeight: androidx.compose.ui.unit.Dp,
    headerAlpha: Float,
    uiState: ChatUiState,
    displayTitle: String,
    otherUser: UserDto?,
    isDark: Boolean,
    onProfileClick: (String) -> Unit
) {
    if (headerHeight > 0.dp) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(headerHeight).graphicsLayer { alpha = headerAlpha },
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.conversation?.type == "direct") {
                    val avatarSize = 200.dp
                    val expansionProgress = ((headerHeight - 100.dp) / 220.dp).coerceIn(0f, 1f)

                    InitialsAvatar(
                        id = displayTitle,
                        avatarUrl = otherUser?.avatarUrl,
                        modifier = Modifier.size(avatarSize).graphicsLayer {
                            val minAvatarSize = 40.dp
                            val currentSize = minAvatarSize + (avatarSize - minAvatarSize) * expansionProgress
                            val scale = currentSize.toPx() / avatarSize.toPx()
                            scaleX = scale
                            scaleY = scale
                            alpha = headerAlpha
                        }.clickable { otherUser?.username?.let(onProfileClick) },
                        fontSize = 80.sp,
                        gradientStart = uiState.conversation?.gradientStartColor?.let { Color(it) },
                        gradientEnd = uiState.conversation?.gradientEndColor?.let { Color(it) }
                    )
                }

                val detailsAlpha = ((headerHeight - 220.dp) / 80.dp).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.fillMaxWidth().graphicsLayer {
                        alpha = detailsAlpha
                        translationY = (1f - detailsAlpha) * 20.dp.toPx()
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (otherUser?.bio?.isNotEmpty() == true) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "\"${otherUser.bio}\"", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (otherUser?.phoneNumber?.isNotEmpty() == true) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = otherUser.phoneNumber, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatFormattingBar(
    onFormattingClick: (String) -> Unit,
    onListFormattingClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val markers = listOf("*" to "B", "_" to "I", "~" to "S", "`" to "</>")
        markers.forEach { (marker, label) ->
            IconButton(onClick = { onFormattingClick(marker) }, modifier = Modifier.size(36.dp)) {
                Text(
                    text = label,
                    fontWeight = if (label == "B") FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (label == "I") FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (label == "S") TextDecoration.LineThrough else TextDecoration.None,
                    fontFamily = if (label == "</>") FontFamily.Monospace else FontFamily.Default,
                    fontSize = if (label == "</>") 13.sp else 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))
        val lists = listOf("bullet" to "•=", "number" to "1.", "roman" to "i.")
        lists.forEach { (type, label) ->
            IconButton(onClick = { onListFormattingClick(type) }, modifier = Modifier.size(36.dp)) {
                Text(text = label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ChatInputBar(
    textMessage: TextFieldValue,
    onTextMessageChange: (TextFieldValue) -> Unit,
    markdownTransformation: VisualTransformation,
    isLoading: Boolean,
    onEmojiClick: () -> Unit,
    onMediaClick: () -> Unit,
    onSendClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)
        ) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                IconButton(onClick = onEmojiClick, modifier = Modifier.size(32.dp).align(Alignment.CenterVertically)) {
                    Icon(Icons.Default.SentimentSatisfiedAlt, contentDescription = "Emoji", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onMediaClick, modifier = Modifier.size(32.dp).align(Alignment.CenterVertically)) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Media", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp).align(Alignment.CenterVertically)) {
                    if (textMessage.text.isEmpty()) {
                        Text("Enter message...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BasicTextField(
                        value = textMessage,
                        onValueChange = onTextMessageChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        visualTransformation = markdownTransformation,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val isSendMode = textMessage.text.isNotBlank()
                AnimatedContent(
                    targetState = isSendMode,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.8f, animationSpec = tween(220, delayMillis = 90)))
                        .togetherWith(fadeOut(animationSpec = tween(90)) + scaleOut(targetScale = 0.8f, animationSpec = tween(90)))
                    },
                    label = "SendMicAnimation",
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) { targetIsSendMode ->
                    if (targetIsSendMode) {
                        IconButton(
                            onClick = { if (!isLoading) onSendClick() },
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(22.dp)) }
                    } else {
                        IconButton(onClick = { }, modifier = Modifier.size(32.dp), colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice", modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    uiModel: MessageUiModel,
    theme: WhisprTheme,
    isDark: Boolean,
    showBubbles: Boolean,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDownloadMedia: (suspend (MessageEntity) -> String?)? = null
) {
    val message = uiModel.message
    val context = LocalContext.current
    var cachedFilePath by remember(message.id) { mutableStateOf<String?>(message.localFilePath) }
    var fullscreenMediaPath by remember { mutableStateOf<String?>(null) }

    if (!message.attachmentUrl.isNullOrEmpty() && cachedFilePath.isNullOrEmpty()) {
        LaunchedEffect(message.id) {
            if (onDownloadMedia != null) {
                val path = onDownloadMedia(message)
                if (path != null) cachedFilePath = path
            }
        }
    }

    val hasAttachment = !message.attachmentUrl.isNullOrEmpty() || message.sizeBytes != null
    val mediaContent: @Composable (() -> Unit)? = if (hasAttachment) {
        {
            val path = cachedFilePath
            if (path != null && File(path).exists()) {
                val isImage = message.mimeType?.startsWith("image/") == true
                if (isImage) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Decrypted photo",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp)).clickable { fullscreenMediaPath = path },
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.6f)).clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.fromFile(File(path)), message.mimeType ?: "video/mp4")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) { }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("▶ Play Video", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(message.mimeType ?: "", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    if (message.syncStatus == "pending") CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Downloading...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    } else null

    ChatBubble(
        content = uiModel.parsedContent,
        time = uiModel.time,
        isSelf = uiModel.isSelf,
        theme = theme,
        isDark = isDark,
        showBubbles = showBubbles,
        syncStatus = message.syncStatus,
        showTimestamp = uiModel.showTimestamp,
        onLongClick = onLongClick,
        mediaContent = mediaContent,
        mimeType = message.mimeType,
        attachmentUrl = message.attachmentUrl ?: message.localFilePath,
        modifier = modifier
    )

    fullscreenMediaPath?.let { path ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { fullscreenMediaPath = null }) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { fullscreenMediaPath = null }, contentAlignment = Alignment.Center) {
                AsyncImage(model = File(path), contentDescription = "Fullscreen", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
            }
        }
    }
}
