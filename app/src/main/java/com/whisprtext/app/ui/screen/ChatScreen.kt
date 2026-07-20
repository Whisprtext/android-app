package com.whisprtext.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.AddPhotoAlternate
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
import com.whisprtext.app.ui.component.DoodleBorderBackground
import com.whisprtext.app.ui.component.InitialsAvatar
import com.whisprtext.app.ui.theme.AppearancePresets
import com.whisprtext.app.ui.theme.WhisprTheme
import com.whisprtext.app.ui.theme.Motion
import com.whisprtext.app.ui.viewmodel.ChatViewModel
import com.whisprtext.app.util.ContactHelper
import com.whisprtext.app.util.MarkdownParser
import com.whisprtext.app.data.model.ThemeMode
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

    // The actual picker launcher — runs after permissions are granted
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                val mime = context.contentResolver.getType(it) ?: "image/jpeg"
                viewModel.sendMediaMessage(it.toString(), mime)
            }
        }
    )

    // Permission launcher — fires pickerLauncher only if all permissions are granted
    val mediaPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            // Open picker if at least images or video permission was granted
            val granted = results.values.any { it }
            if (granted) pickerLauncher.launch("*/*")
        }
    )

    // Helper: check perms and launch picker or request perms
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

    val headerTransition = updateTransition(targetState = isHeaderExpanded, label = "HeaderExpansion")

    val headerHeight by headerTransition.animateDp(
        transitionSpec = {
            if (targetState) {
                tween(Motion.LongDuration3, easing = Motion.EmphasizedDecelerateEasing)
            } else {
                tween(Motion.LongDuration3, easing = Motion.EmphasizedAccelerateEasing)
            }
        },
        label = "HeaderHeight"
    ) { expanded ->
        if (expanded) 320.dp else 0.dp
    }

    val headerAlpha by headerTransition.animateFloat(
        transitionSpec = { tween(Motion.MediumDuration2) },
        label = "HeaderAlpha"
    ) { expanded ->
        if (expanded) 1f else 0f
    }

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
        } else {
            "Chat"
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

    fun toggleListFormatting(listType: String) {
        val selection = textMessage.selection
        val text = textMessage.text

        val lines = text.split('\n')
        val lineOffsets = mutableListOf<Pair<Int, Int>>()
        var currentOffset = 0
        for (line in lines) {
            val start = currentOffset
            val end = currentOffset + line.length
            lineOffsets.add(Pair(start, end))
            currentOffset = end + 1
        }

        val selectedLineIndices = mutableListOf<Int>()
        val selMin = selection.min
        val selMax = selection.max

        for (i in lineOffsets.indices) {
            val (start, end) = lineOffsets[i]
            if (selection.collapsed) {
                if (selMin in start..end) {
                    selectedLineIndices.add(i)
                }
            } else {
                if (Math.max(start, selMin) <= Math.min(end, selMax)) {
                    selectedLineIndices.add(i)
                }
            }
        }

        if (selectedLineIndices.isEmpty() && lines.isNotEmpty()) {
            selectedLineIndices.add(0)
        }

        val bulletRegex = Regex("""^(\s*)([-*•])\s+""")
        val numberRegex = Regex("""^(\s*)(\d+)\.\s+""")
        val romanRegex = Regex("""^(\s*)([ivxldcmIVXLDCM]+)\.\s+""")

        val newLines = lines.toMutableList()

        for (idx in selectedLineIndices) {
            if (idx >= newLines.size) continue
            val line = newLines[idx]

            val bulletMatch = bulletRegex.find(line)
            val numberMatch = numberRegex.find(line)
            val romanMatch = romanRegex.find(line)

            val contentWithoutPrefix = when {
                bulletMatch != null -> line.substring(bulletMatch.range.last + 1)
                numberMatch != null -> line.substring(numberMatch.range.last + 1)
                romanMatch != null -> line.substring(romanMatch.range.last + 1)
                else -> line
            }

            val isCurrentSameType = when (listType) {
                "bullet" -> bulletMatch != null
                "number" -> { numberMatch != null }
                "roman" -> { romanMatch != null }
                else -> false
            }

            if (isCurrentSameType) {
                newLines[idx] = contentWithoutPrefix
            } else {
                val prefix = when (listType) {
                    "bullet" -> "\t- "
                    "number" -> {
                        var nextNum = 1
                        if (idx > 0) {
                            val prevLine = newLines[idx - 1]
                            val prevNumMatch = numberRegex.find(prevLine)
                            if (prevNumMatch != null) {
                                val numStr = prevNumMatch.groupValues[2]
                                nextNum = (numStr.toIntOrNull() ?: 0) + 1
                            }
                        }
                        "\t$nextNum. "
                    }
                    "roman" -> {
                        var nextRoman = "i"
                        if (idx > 0) {
                            val prevLine = newLines[idx - 1]
                            val prevRomanMatch = romanRegex.find(prevLine)
                            if (prevRomanMatch != null) {
                                val prevRoman = prevRomanMatch.groupValues[2]
                                nextRoman = MarkdownParser.incrementRoman(prevRoman)
                            }
                        }
                        "\t$nextRoman. "
                    }
                    else -> ""
                }
                newLines[idx] = prefix + contentWithoutPrefix
            }
        }

        val newText = newLines.joinToString("\n")
        val newCursor = newText.length

        textMessage = TextFieldValue(
            text = newText,
            selection = androidx.compose.ui.text.TextRange(newCursor, newCursor)
        )
    }

    fun onTextMessageChange(newValue: TextFieldValue) {
        val oldValue = textMessage
        val oldText = oldValue.text
        val newText = newValue.text
        
        if (newText.length == oldText.length + 1 && 
            newValue.selection.collapsed && 
            newValue.selection.start > 0 &&
            newText[newValue.selection.start - 1] == '\n'
        ) {
            val newlineIdx = newValue.selection.start - 1
            val textBeforeNewline = newText.substring(0, newlineIdx)
            val lastNewlineBefore = textBeforeNewline.lastIndexOf('\n')
            val lastLine = if (lastNewlineBefore == -1) {
                textBeforeNewline
            } else {
                textBeforeNewline.substring(lastNewlineBefore + 1)
            }
            
            val bulletRegex = Regex("""^(\s*)([-*•])\s+(.*)$""")
            val numberRegex = Regex("""^(\s*)(\d+)\.\s+(.*)$""")
            val romanRegex = Regex("""^(\s*)([ivxldcmIVXLDCM]+)\.\s+(.*)$""")
            
            val bulletMatch = bulletRegex.matchEntire(lastLine)
            val numberMatch = numberRegex.matchEntire(lastLine)
            val romanMatch = romanRegex.matchEntire(lastLine)
            
            when {
                bulletMatch != null -> {
                    val indent = bulletMatch.groupValues[1]
                    val marker = bulletMatch.groupValues[2]
                    val content = bulletMatch.groupValues[3]
                    
                    if (content.isBlank()) {
                        val lineStartIdx = newlineIdx - lastLine.length
                        val cleanedText = oldText.substring(0, lineStartIdx) + oldText.substring(newlineIdx)
                        textMessage = TextFieldValue(
                            text = cleanedText,
                            selection = androidx.compose.ui.text.TextRange(lineStartIdx)
                        )
                        return
                    } else {
                        val prefix = "\n$indent$marker "
                        val autoText = newText.substring(0, newlineIdx) + prefix + newText.substring(newlineIdx + 1)
                        val nextCursor = newlineIdx + prefix.length
                        textMessage = TextFieldValue(
                            text = autoText,
                            selection = androidx.compose.ui.text.TextRange(nextCursor)
                        )
                        return
                    }
                }
                numberMatch != null -> {
                    val indent = numberMatch.groupValues[1]
                    val numStr = numberMatch.groupValues[2]
                    val content = numberMatch.groupValues[3]
                    
                    if (content.isBlank()) {
                        val lineStartIdx = newlineIdx - lastLine.length
                        val cleanedText = oldText.substring(0, lineStartIdx) + oldText.substring(newlineIdx)
                        textMessage = TextFieldValue(
                            text = cleanedText,
                            selection = androidx.compose.ui.text.TextRange(lineStartIdx)
                        )
                        return
                    } else {
                        val nextNum = (numStr.toIntOrNull() ?: 1) + 1
                        val prefix = "\n$indent$nextNum. "
                        val autoText = newText.substring(0, newlineIdx) + prefix + newText.substring(newlineIdx + 1)
                        val nextCursor = newlineIdx + prefix.length
                        textMessage = TextFieldValue(
                            text = autoText,
                            selection = androidx.compose.ui.text.TextRange(nextCursor)
                        )
                        return
                    }
                }
                romanMatch != null -> {
                    val indent = romanMatch.groupValues[1]
                    val romanStr = romanMatch.groupValues[2]
                    val content = romanMatch.groupValues[3]
                    
                    if (content.isBlank()) {
                        val lineStartIdx = newlineIdx - lastLine.length
                        val cleanedText = oldText.substring(0, lineStartIdx) + oldText.substring(newlineIdx)
                        textMessage = TextFieldValue(
                            text = cleanedText,
                            selection = androidx.compose.ui.text.TextRange(lineStartIdx)
                        )
                        return
                    } else {
                        val nextRoman = MarkdownParser.incrementRoman(romanStr)
                        val prefix = "\n$indent$nextRoman. "
                        val autoText = newText.substring(0, newlineIdx) + prefix + newText.substring(newlineIdx + 1)
                        val nextCursor = newlineIdx + prefix.length
                        textMessage = TextFieldValue(
                            text = autoText,
                            selection = androidx.compose.ui.text.TextRange(nextCursor)
                        )
                        return
                    }
                }
            }
        }
        
        textMessage = newValue
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {
        if (appearance.useDoodles) {
            val doodleModifier = remember { Modifier.graphicsLayer() }
            DoodleBorderBackground(
                style = appearance.doodleStyle,
                alpha = appearance.doodleAlpha * 0.6f, // Slightly more subtle
                color = Color.Black.copy(alpha = 0.8f), // Always black outline style as requested
                modifier = doodleModifier
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = if (isHeaderExpanded) RoundedCornerShape(0.dp) else RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                    shadowElevation = if (isHeaderExpanded) 0.dp else 4.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = if (isDark && !isHeaderExpanded) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }

                        val targetUsername = uiState.conversation?.username
                        val isDirect = uiState.conversation?.type == "direct"

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = isDirect && !targetUsername.isNullOrEmpty()) {
                                    if (!isHeaderExpanded) {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        isHeaderExpanded = true
                                    } else {
                                        isHeaderExpanded = false
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isHeaderExpanded) {
                                if (isDirect) {
                                    InitialsAvatar(
                                        id = displayTitle,
                                        avatarUrl = otherUser?.avatarUrl,
                                        modifier = Modifier.size(36.dp),
                                        fontSize = 16.sp,
                                        gradientStart = uiState.conversation?.gradientStartColor?.let { Color(it) },
                                        gradientEnd = uiState.conversation?.gradientEndColor?.let { Color(it) }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }

                                Column {
                                    Text(
                                        text = displayTitle,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (isDirect && otherUser?.bio?.isNotEmpty() == true) {
                                        Text(
                                            text = otherUser.bio,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { /* Voice Call */ }) {
                                Icon(Icons.Default.Phone, contentDescription = "Voice Call")
                            }
                            IconButton(onClick = { /* Video Call */ }) {
                                Icon(Icons.Default.Videocam, contentDescription = "Video Call")
                            }
                            IconButton(onClick = { /* Menu */ }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    }
                }
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
                    ) {
                        if (isHeaderExpanded) {
                            isHeaderExpanded = false
                        }
                    }
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
                        val showTimestampIndices = remember(uiState.messages) {
                            // uiState.messages is ORDER BY createdAt DESC (newest first)
                            // To process chronologically, we reverse it
                            val chronological = uiState.messages.reversed()
                            val result = mutableSetOf<String>()
                            if (chronological.isEmpty()) return@remember result

                            var currentBlockSender = chronological[0].senderId
                            var lastMessageTimeInBlock = chronological[0].createdAt
                            var currentBlockCount = 0

                            for (i in chronological.indices) {
                                val msg = chronological[i]
                                val timeGap = msg.createdAt - lastMessageTimeInBlock

                                val senderChanged = msg.senderId != currentBlockSender
                                val timeGapExceeded = i > 0 && timeGap >= 300_000 // 5 minutes gap from PREVIOUS message in chronological order
                                val countExceeded = currentBlockCount >= 10

                                if (senderChanged || timeGapExceeded || countExceeded) {
                                    // End previous block: the message BEFORE this one was the last in its block
                                    if (i > 0) {
                                        result.add(chronological[i - 1].id)
                                    }
                                    currentBlockSender = msg.senderId
                                    lastMessageTimeInBlock = msg.createdAt
                                    currentBlockCount = 1
                                } else {
                                    currentBlockCount++
                                    lastMessageTimeInBlock = msg.createdAt
                                }
                            }
                            // Always add the last message of the entire list as the end of the final block
                            result.add(chronological.last().id)
                            result
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            reverseLayout = true
                        ) {
                            itemsIndexed(
                                items = uiState.messages,
                                key = { _, message -> message.id },
                                contentType = { _, _ -> "message" }
                            ) { index, message ->
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
                                    theme = theme,
                                    isDark = isDark,
                                    showBubbles = appearance.showChatBubbles,
                                    showTimestamp = showTimestampIndices.contains(message.id),
                                    onLongClick = remember(message.id) { { messageToDelete = message } }
                                )
                            }
                        }
                    }

                    // Expansion overlay
                    if (headerHeight > 0.dp) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(headerHeight)
                                .graphicsLayer { alpha = headerAlpha },
                            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                            shadowElevation = 8.dp,
                            color = MaterialTheme.colorScheme.surface,
                            border = if (isDark) BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            ) else null
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isHeaderExpanded = false }
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val targetUsername = uiState.conversation?.username
                                if (uiState.conversation?.type == "direct") {
                                    val avatarSize = 200.dp
                                    val minAvatarSize = 40.dp
                                    val expansionProgress = remember(headerHeight) {
                                        ((headerHeight - 100.dp) / 220.dp).coerceIn(0f, 1f)
                                    }

                                    InitialsAvatar(
                                        id = displayTitle,
                                        avatarUrl = otherUser?.avatarUrl,
                                        modifier = Modifier
                                            .size(avatarSize)
                                            .graphicsLayer {
                                                val currentSize = minAvatarSize + (avatarSize - minAvatarSize) * expansionProgress
                                                val scale = currentSize.toPx() / avatarSize.toPx()
                                                scaleX = scale
                                                scaleY = scale
                                                alpha = headerAlpha
                                            }
                                            .clickable {
                                                if (targetUsername != null) {
                                                    onProfileClick(targetUsername)
                                                }
                                            },
                                        fontSize = 80.sp,
                                        gradientStart = uiState.conversation?.gradientStartColor?.let { Color(it) },
                                        gradientEnd = uiState.conversation?.gradientEndColor?.let { Color(it) }
                                    )
                                }

                                val detailsAlpha = remember(headerHeight) {
                                    ((headerHeight - 220.dp) / 80.dp).coerceIn(0f, 1f)
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer { 
                                            alpha = detailsAlpha
                                            translationY = (1f - detailsAlpha) * 20.dp.toPx()
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (otherUser?.bio?.isNotEmpty() == true) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "\"${otherUser.bio}\"",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontStyle = FontStyle.Italic,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (otherUser?.phoneNumber?.isNotEmpty() == true) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Phone,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = otherUser.phoneNumber,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (uiState.error != null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                        Snackbar(
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

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    shadowElevation = 12.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
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

                            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

                            IconButton(
                                onClick = { toggleListFormatting("bullet") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(
                                    text = "•=",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(
                                onClick = { toggleListFormatting("number") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(
                                    text = "1.",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(
                                onClick = { toggleListFormatting("roman") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(
                                    text = "i.",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = { /* Emoji */ },
                                        modifier = Modifier.size(32.dp).align(Alignment.CenterVertically)
                                    ) {
                                        Icon(
                                            Icons.Default.SentimentSatisfiedAlt,
                                            contentDescription = "Emoji",
                                            modifier = Modifier.size(22.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { launchMediaPicker() },
                                        modifier = Modifier.size(32.dp).align(Alignment.CenterVertically)
                                    ) {
                                        Icon(
                                            Icons.Default.AddPhotoAlternate,
                                            contentDescription = "Media",
                                            modifier = Modifier.size(22.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                            .align(Alignment.CenterVertically)
                                    ) {
                                        if (textMessage.text.isEmpty()) {
                                            Text(
                                                "Enter message...",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        BasicTextField(
                                            value = textMessage,
                                            onValueChange = { onTextMessageChange(it) },
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            visualTransformation = markdownTransformation,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    val isSendMode = textMessage.text.isNotBlank()
                                    AnimatedContent(
                                        targetState = isSendMode,
                                        transitionSpec = {
                                            (fadeIn(animationSpec = tween(220, delayMillis = 90)) + 
                                             scaleIn(initialScale = 0.8f, animationSpec = tween(220, delayMillis = 90)))
                                            .togetherWith(fadeOut(animationSpec = tween(90)) + scaleOut(targetScale = 0.8f, animationSpec = tween(90)))
                                        },
                                        label = "SendMicAnimation",
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    ) { targetIsSendMode ->
                                        if (targetIsSendMode) {
                                            IconButton(
                                                onClick = {
                                                    if (!uiState.isLoading) {
                                                        viewModel.sendMessage(textMessage.text)
                                                        textMessage = TextFieldValue("")
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp),
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(22.dp))
                                            }
                                        } else {
                                            IconButton(
                                                onClick = { /* Record Voice */ },
                                                modifier = Modifier.size(32.dp),
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            ) {
                                                Icon(Icons.Default.Mic, contentDescription = "Voice", modifier = Modifier.size(22.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
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

@Composable
fun MessageBubble(
    message: MessageEntity,
    isSelf: Boolean,
    isGroupHeader: Boolean,
    isGroupFooter: Boolean,
    theme: WhisprTheme,
    isDark: Boolean,
    showBubbles: Boolean,
    showTimestamp: Boolean = true,
    onLongClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ChatViewModel>()
    var cachedFilePath by remember(message.id) { mutableStateOf<String?>(message.localFilePath) }
    var fullscreenMediaPath by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Automatically trigger lazy download & decrypt when bubble is shown
    if (!message.attachmentUrl.isNullOrEmpty() && cachedFilePath.isNullOrEmpty()) {
        LaunchedEffect(message.id) {
            val path = viewModel.getDecryptedFilePath(message)
            if (path != null) {
                cachedFilePath = path
            }
        }
    }

    val displayText = remember(message.decryptedContent, message.decryptionStatus) {
        val raw = message.decryptedContent
        when {
            message.decryptionStatus == "failed" ||
                message.isDecryptionFailed ->
                com.whisprtext.app.crypto.SignalKeyManager.DISPLAY_DECRYPT_FAILED
            com.whisprtext.app.crypto.SignalKeyManager.isLikelyCiphertext(raw) ->
                com.whisprtext.app.crypto.SignalKeyManager.DISPLAY_DECRYPT_FAILED
            else -> raw
        }
    }
    val parsedContent = remember(displayText) {
        MarkdownParser.parse(displayText, hideMarkers = true)
    }

    val timeStr = remember(message.createdAt) {
        val timeInstant = java.time.Instant.ofEpochMilli(message.createdAt)
        java.time.LocalDateTime.ofInstant(timeInstant, java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                fullscreenMediaPath = path
                            },
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.fromFile(File(path)), message.mimeType ?: "video/mp4")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (message.syncStatus == "pending") {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else if (message.syncStatus == "failed") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    // Retry upload via sync action on viewmodel
                                    viewModel.sync()
                                }
                            }
                        ) {
                            Text("✕ Upload failed. Tap to retry.", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Downloading media...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    } else null

    ChatBubble(
        content = parsedContent,
        time = timeStr,
        isSelf = isSelf,
        isGroupHeader = isGroupHeader,
        isGroupFooter = isGroupFooter,
        theme = theme,
        isDark = isDark,
        showBubbles = showBubbles,
        syncStatus = message.syncStatus,
        showTimestamp = showTimestamp,
        onLongClick = onLongClick,
        mediaContent = mediaContent
    )

    fullscreenMediaPath?.let { path ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { fullscreenMediaPath = null }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullscreenMediaPath = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = File(path),
                    contentDescription = "Fullscreen E2EE image",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
