package com.whisprtext.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.text.AnnotatedString
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.model.AppearanceSettings
import com.whisprtext.app.data.repository.ChatRepository
import com.whisprtext.app.data.repository.ContactRepository
import com.whisprtext.app.util.MarkdownParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.whisprtext.app.data.remote.model.UserDto
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class MessageUiModel(
    val message: MessageEntity,
    val parsedContent: AnnotatedString,
    val time: String,
    val isSelf: Boolean,
    val isGroupHeader: Boolean,
    val isGroupFooter: Boolean,
    val isSameSenderAsNext: Boolean,
    val showTimestamp: Boolean
)

data class ChatUiState(
    val messages: List<MessageUiModel> = emptyList(),
    val contactsMap: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val conversation: ConversationEntity? = null,
    val otherUser: UserDto? = null,
    val appearanceSettings: AppearanceSettings = AppearanceSettings()
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    val conversationId: String,
    private val chatRepository: ChatRepository,
    private val contactRepository: ContactRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _otherUser = MutableStateFlow<UserDto?>(null)
    private val _userId = MutableStateFlow(preferencesManager.cachedUserId.orEmpty())

    val uiState: StateFlow<ChatUiState> = combine(
        chatRepository.getMessages(conversationId),
        chatRepository.getConversationFlow(conversationId),
        contactRepository.contactsMap,
        _otherUser,
        _isLoading,
        _error,
        preferencesManager.appearanceSettings,
        _userId
    ) { flowResults ->
        val messages = flowResults[0] as List<MessageEntity>
        val conversation = flowResults[1] as? ConversationEntity
        val contactsMap = flowResults[2] as Map<String, String>
        val otherUser = flowResults[3] as? UserDto
        val isLoading = flowResults[4] as Boolean
        val error = flowResults[5] as? String
        val appearance = flowResults[6] as AppearanceSettings
        val currentUserId = flowResults[7] as String

        val messageUiModels = transformMessagesToUiModels(messages, currentUserId)

        // Prefer live conversation.avatarUrl (updated when profile is refreshed) over stale otherUser.
        val mergedOther = when {
            otherUser == null && conversation?.username != null -> UserDto(
                id = "",
                username = conversation.username,
                displayName = conversation.title.orEmpty(),
                avatarUrl = conversation.avatarUrl.orEmpty(),
                phoneNumber = conversation.phoneNumber,
                bio = ""
            )
            otherUser != null && conversation != null -> otherUser.copy(
                avatarUrl = conversation.avatarUrl?.takeIf { it.isNotBlank() } ?: otherUser.avatarUrl,
                displayName = conversation.title?.takeIf { it.isNotBlank() } ?: otherUser.displayName,
                phoneNumber = conversation.phoneNumber ?: otherUser.phoneNumber
            )
            else -> otherUser
        }

        ChatUiState(
            messages = messageUiModels,
            contactsMap = contactsMap,
            isLoading = isLoading,
            error = error,
            conversation = conversation,
            otherUser = mergedOther,
            appearanceSettings = appearance
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = buildInitialUiState(conversationId, chatRepository, preferencesManager)
    )

    val currentUserId: StateFlow<String> = _userId

    init {
        chatRepository.activeConversationId = conversationId
        // Opening a chat clears the unread badge immediately
        viewModelScope.launch {
            chatRepository.markConversationAsRead(conversationId)
        }
        viewModelScope.launch {
            preferencesManager.userId.collect { id ->
                _userId.value = id ?: ""
            }
        }
        viewModelScope.launch {
            chatRepository.getMessages(conversationId).collect {
                if (chatRepository.isAppInForeground && chatRepository.activeConversationId == conversationId) {
                    chatRepository.markConversationAsRead(conversationId)
                }
            }
        }
        // Local-first contact profile for chat header; network refresh happens on profile open.
        // Combines conversation + cached profile so avatar updates propagate live.
        viewModelScope.launch {
            chatRepository.getConversationFlow(conversationId)
                .flatMapLatest { conversation ->
                    val username = conversation?.username
                    if (conversation == null || conversation.type != "direct" || username.isNullOrEmpty()) {
                        flowOf(null to conversation)
                    } else {
                        chatRepository.observeProfileByUsername(username)
                            .map { profile -> profile to conversation }
                    }
                }
                .collect { (cached, conversation) ->
                    if (conversation == null) return@collect
                    val username = conversation.username ?: return@collect
                    _otherUser.value = cached ?: UserDto(
                        id = "",
                        username = username,
                        displayName = conversation.title.orEmpty(),
                        avatarUrl = conversation.avatarUrl.orEmpty(),
                        phoneNumber = conversation.phoneNumber
                    )
                }
        }
        sync()
    }

    override fun onCleared() {
        super.onCleared()
        if (chatRepository.activeConversationId == conversationId) {
            chatRepository.activeConversationId = null
        }
    }

    fun sync() {
        // No-op to prevent message history backfill.
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val senderId = _userId.value
            if (senderId.isNotBlank()) {
                // Device UUID is resolved inside ChatRepository from PreferencesManager
                chatRepository.sendMessage(conversationId, content, senderId, "")
            }
        }
    }

    fun deleteMessage(messageId: String, forEveryone: Boolean) {
        viewModelScope.launch {
            try {
                chatRepository.deleteMessage(messageId, forEveryone)
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = e.message ?: "Failed to delete message"
            }
        }
    }

    fun sendMediaMessage(
        uriString: String,
        mimeType: String,
        content: String = "[Media]",
        extraUris: List<Pair<String, String>> = emptyList()
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val senderId = _userId.value
                if (senderId.isNotBlank()) {
                    chatRepository.sendMediaMessage(
                        conversationId = conversationId,
                        uriString = uriString,
                        mimeType = mimeType,
                        senderId = senderId,
                        senderDeviceId = "",
                        content = content,
                        extraUris = extraUris
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = e.message ?: "Failed to send media file"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendStickerMessage(stickerPath: String, mimeType: String) {
        sendMediaMessage(
            uriString = stickerPath,
            mimeType = mimeType,
            content = "[Sticker]"
        )
    }

    suspend fun getDecryptedFilePath(message: MessageEntity): String? {
        return try {
            chatRepository.downloadAndDecryptMedia(message)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        fun transformMessagesToUiModels(
            messages: List<MessageEntity>,
            currentUserId: String
        ): List<MessageUiModel> {
            val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
            val zoneId = java.time.ZoneId.systemDefault()

            val chronological = messages.reversed()
            val showTimestampIds = mutableSetOf<String>()
            if (chronological.isNotEmpty()) {
                var currentBlockSender = chronological[0].senderId
                var lastMessageTimeInBlock = chronological[0].createdAt
                var currentBlockCount = 0

                for (i in chronological.indices) {
                    val msg = chronological[i]
                    val timeGap = msg.createdAt - lastMessageTimeInBlock
                    val senderChanged = msg.senderId != currentBlockSender
                    val timeGapExceeded = i > 0 && timeGap >= 300_000
                    val countExceeded = currentBlockCount >= 10

                    if (senderChanged || timeGapExceeded || countExceeded) {
                        if (i > 0) showTimestampIds.add(chronological[i - 1].id)
                        currentBlockSender = msg.senderId
                        lastMessageTimeInBlock = msg.createdAt
                        currentBlockCount = 1
                    } else {
                        currentBlockCount++
                        lastMessageTimeInBlock = msg.createdAt
                    }
                }
                showTimestampIds.add(chronological.last().id)
            }

            return messages.mapIndexed { index, message ->
                val isSelf = currentUserId.isNotEmpty() && message.senderId == currentUserId

                val isSameSenderAsNext = index < messages.size - 1 &&
                        messages[index].senderId == messages[index + 1].senderId
                val isWithinTimeAsNext = index < messages.size - 1 &&
                        Math.abs(messages[index].createdAt - messages[index + 1].createdAt) < 300_000

                val isSameSenderAsPrev = index > 0 &&
                        messages[index].senderId == messages[index - 1].senderId
                val isWithinTimeAsPrev = index > 0 &&
                        Math.abs(messages[index].createdAt - messages[index - 1].createdAt) < 300_000

                val isGroupHeader = !(isSameSenderAsNext && isWithinTimeAsNext)
                val isGroupFooter = !(isSameSenderAsPrev && isWithinTimeAsPrev)

                val raw = message.decryptedContent
                val displayText = when {
                    message.decryptionStatus == "failed" || message.isDecryptionFailed ->
                        com.whisprtext.app.crypto.SignalKeyManager.DISPLAY_DECRYPT_FAILED
                    com.whisprtext.app.crypto.SignalKeyManager.isLikelyCiphertext(raw) ->
                        com.whisprtext.app.crypto.SignalKeyManager.DISPLAY_DECRYPT_FAILED
                    else -> raw
                }

                MessageUiModel(
                    message = message,
                    parsedContent = MarkdownParser.parse(displayText, hideMarkers = true),
                    time = java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(message.createdAt),
                        zoneId
                    ).format(timeFormatter),
                    isSelf = isSelf,
                    isGroupHeader = isGroupHeader,
                    isGroupFooter = isGroupFooter,
                    isSameSenderAsNext = isSameSenderAsNext,
                    showTimestamp = showTimestampIds.contains(message.id)
                )
            }
        }

        fun buildInitialUiState(
            conversationId: String,
            chatRepository: ChatRepository,
            preferencesManager: PreferencesManager
        ): ChatUiState {
            val cached = chatRepository.getCachedMessages(conversationId)
            val initialUserId = preferencesManager.cachedUserId.orEmpty()
            val initialAppearance = preferencesManager.cachedAppearanceSettings

            if (cached.isEmpty()) return ChatUiState(
                isLoading = false,
                appearanceSettings = initialAppearance
            )

            val messageUiModels = transformMessagesToUiModels(cached, initialUserId)

            return ChatUiState(
                messages = messageUiModels,
                isLoading = false,
                appearanceSettings = initialAppearance
            )
        }
    }
}
