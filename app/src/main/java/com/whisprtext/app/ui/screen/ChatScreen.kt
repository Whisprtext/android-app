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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.ui.component.DoodleBackground
import com.whisprtext.app.ui.theme.AppearancePresets
import com.whisprtext.app.ui.theme.ChatTheme
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
    val otherUser = uiState.otherUser
    val currentUserId by viewModel.currentUserId.collectAsState()
    val appearance = uiState.appearanceSettings
    val isDark = isSystemInDarkTheme()
    val theme = AppearancePresets.getTheme(appearance.presetId)

    val backgroundColor = if (isDark) theme.backgroundColorDark else theme.backgroundColorLight
    val gradientColors = if (isDark) theme.gradientColorsDark else theme.gradientColorsLight
    
    val backgroundModifier = if (theme.isGradient) {
        Modifier.background(Brush.verticalGradient(gradientColors))
    } else {
        Modifier.background(backgroundColor)
    }

    var textMessage by remember { mutableStateOf(TextFieldValue("")) }
    var messageToDelete by remember { mutableStateOf<MessageEntity?>(null) }
    var isHeaderExpanded by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val context = LocalContext.current
    val contactsMap = remember(context) { ContactHelper.getContactsMap(context) }
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
                "number" -> numberMatch != null
                "roman" -> romanMatch != null
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
            DoodleBackground(
                style = appearance.doodleStyle,
                alpha = appearance.doodleAlpha,
                color = if (isDark) Color.White else Color.Black
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth().animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
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
                                            id = targetUsername ?: displayTitle,
                                            avatarUrl = otherUser?.avatarUrl,
                                            modifier = Modifier.size(36.dp),
                                            fontSize = 16.sp
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
                                        text = "Profile",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = isHeaderExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
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
                                    InitialsAvatar(
                                        id = targetUsername ?: displayTitle,
                                        avatarUrl = otherUser?.avatarUrl,
                                        modifier = Modifier.size(100.dp),
                                        fontSize = 40.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    textAlign = TextAlign.Center
                                )

                                if (otherUser?.bio?.isNotEmpty() == true) {
                                    Spacer(modifier = Modifier.height(12.dp))
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

                                Spacer(modifier = Modifier.height(24.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (targetUsername != null) {
                                                onProfileClick(targetUsername)
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "View full profile",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "View Full Profile",
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                    theme = theme,
                                    isDark = isDark,
                                    onLongClick = { messageToDelete = message }
                                )
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
                                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            OutlinedTextField(
                                value = textMessage,
                                onValueChange = { onTextMessageChange(it) },
                                placeholder = { Text("Enter message...") },
                                modifier = Modifier
                                    .weight(1f),
                                singleLine = false,
                                maxLines = 5,
                                shape = RoundedCornerShape(20.dp),
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
    theme: ChatTheme,
    isDark: Boolean,
    onLongClick: () -> Unit
) {
    val bubbleColor = if (isSelf) {
        if (isDark) theme.selfBubbleColorDark else theme.selfBubbleColorLight
    } else {
        if (isDark) theme.otherBubbleColorDark else theme.otherBubbleColorLight
    }
    val textColor = if (isDark) Color.White else Color.Black
    val alignment = if (isSelf) Alignment.End else Alignment.Start

    val bubbleShape = RoundedCornerShape(
        topStart = if (!isSelf) 0.dp else (if (isGroupHeader) 12.dp else 4.dp),
        topEnd = if (isSelf) 0.dp else (if (isGroupHeader) 12.dp else 4.dp),
        bottomStart = if (!isSelf) 0.dp else (if (isGroupFooter) 12.dp else 4.dp),
        bottomEnd = if (isSelf) 0.dp else (if (isGroupFooter) 12.dp else 4.dp)
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
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
