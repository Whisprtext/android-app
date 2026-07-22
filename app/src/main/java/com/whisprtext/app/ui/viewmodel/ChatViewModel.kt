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

import com.whisprtext.app.translation.ModelDownloadState
import com.whisprtext.app.translation.TranslationModelRepository
import com.whisprtext.app.translation.TranslationRepository
import com.whisprtext.app.translation.TranslationResult

sealed class MessageTranslationState {
    object None : MessageTranslationState()
    object Translating : MessageTranslationState()
    data class Translated(
        val translatedText: String,
        val sourceLanguage: String,
        val targetLanguage: String,
        val showOriginal: Boolean = false
    ) : MessageTranslationState()
    data class Error(val message: String) : MessageTranslationState()
    object ModelRequired : MessageTranslationState()
    data class Skipped(val reason: String) : MessageTranslationState()
    object LanguageUncertain : MessageTranslationState()
}

data class MessageUiModel(
    val message: MessageEntity,
    val parsedContent: AnnotatedString,
    val time: String,
    val isSelf: Boolean,
    val isGroupHeader: Boolean,
    val isGroupFooter: Boolean,
    val isSameSenderAsNext: Boolean,
    val showTimestamp: Boolean,
    val translationState: MessageTranslationState = MessageTranslationState.None
)

data class ChatUiState(
    val messages: List<MessageUiModel> = emptyList(),
    val contactsMap: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val conversation: ConversationEntity? = null,
    val otherUser: UserDto? = null,
    val appearanceSettings: AppearanceSettings = AppearanceSettings(),
    val isTranslationEnabled: Boolean = false,
    val preferredTargetLanguage: String = "eng_Latn",
    val modelDownloadState: ModelDownloadState = ModelDownloadState.NotDownloaded
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    val conversationId: String,
    private val chatRepository: ChatRepository,
    private val contactRepository: ContactRepository,
    private val preferencesManager: PreferencesManager,
    private val translationRepository: TranslationRepository? = null,
    private val translationModelRepository: TranslationModelRepository? = null
) : ViewModel() {

    private val _translationStateMap = MutableStateFlow<Map<String, MessageTranslationState>>(emptyMap())
    private val _activeTranslationJobs = mutableSetOf<String>()

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
        _userId,
        _translationStateMap,
        preferencesManager.isTranslationEnabled,
        preferencesManager.preferredTargetLanguage,
        translationModelRepository?.downloadState ?: MutableStateFlow(ModelDownloadState.NotDownloaded)
    ) { flowResults ->
        val messages = flowResults[0] as List<MessageEntity>
        val conversation = flowResults[1] as? ConversationEntity
        val contactsMap = flowResults[2] as Map<String, String>
        val otherUser = flowResults[3] as? UserDto
        val isLoading = flowResults[4] as Boolean
        val error = flowResults[5] as? String
        val appearance = flowResults[6] as AppearanceSettings
        val currentUserId = flowResults[7] as String
        val translationStates = flowResults[8] as Map<String, MessageTranslationState>
        val isTranslationEnabled = flowResults[9] as Boolean
        val targetLang = flowResults[10] as String
        val dlState = flowResults[11] as ModelDownloadState

        val messageUiModels = transformMessagesToUiModels(messages, currentUserId, translationStates)

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
            appearanceSettings = appearance,
            isTranslationEnabled = isTranslationEnabled,
            preferredTargetLanguage = targetLang,
            modelDownloadState = dlState
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

    fun translateMessage(messageId: String) {
        val repo = translationRepository ?: return
        val modelRepo = translationModelRepository
        val state = uiState.value

        if (_activeTranslationJobs.contains(messageId)) return

        viewModelScope.launch {
            if (modelRepo != null && !modelRepo.isModelReady()) {
                _translationStateMap.update { current ->
                    current + (messageId to MessageTranslationState.ModelRequired)
                }
                return@launch
            }

            _activeTranslationJobs.add(messageId)
            _translationStateMap.update { current ->
                current + (messageId to MessageTranslationState.Translating)
            }

            val msgUi = state.messages.firstOrNull { it.message.id == messageId }
            if (msgUi == null) {
                _activeTranslationJobs.remove(messageId)
                return@launch
            }

            val rawText = msgUi.message.decryptedContent
            if (rawText.isBlank() || msgUi.message.decryptionStatus == "failed" || msgUi.message.isDecryptionFailed || com.whisprtext.app.crypto.SignalKeyManager.isLikelyCiphertext(rawText)) {
                _activeTranslationJobs.remove(messageId)
                _translationStateMap.update { current ->
                    current + (messageId to MessageTranslationState.Skipped("Cannot translate encrypted text"))
                }
                return@launch
            }

            val result = repo.getOrTranslateMessage(
                messageId = messageId,
                text = rawText,
                targetLanguage = state.preferredTargetLanguage
            )

            _activeTranslationJobs.remove(messageId)
            val logVal = when (result) {
                is TranslationResult.Success -> result.translatedText
                is TranslationResult.Skipped -> "Skipped: ${result.reason}"
                is TranslationResult.Error -> "Error: ${result.message}"
            }
            try { android.util.Log.i("ChatViewModel", "[ViewModelResult]\nmessageId=$messageId\ntranslation=$logVal") } catch (_: Throwable) { println("INFO: [ChatViewModel] [ViewModelResult]\nmessageId=$messageId\ntranslation=$logVal") }

            _translationStateMap.update { current ->
                val newState = when (result) {
                    is TranslationResult.Success -> MessageTranslationState.Translated(
                        translatedText = result.translatedText,
                        sourceLanguage = result.sourceLanguage,
                        targetLanguage = result.targetLanguage,
                        showOriginal = false
                    )
                    is TranslationResult.Skipped -> {
                        if (result.reason.contains("Ambiguous", ignoreCase = true) || result.reason.contains("uncertain", ignoreCase = true)) {
                            MessageTranslationState.LanguageUncertain
                        } else {
                            MessageTranslationState.Skipped(result.reason)
                        }
                    }
                    is TranslationResult.Error -> MessageTranslationState.Error(result.message)
                }
                current + (messageId to newState)
            }
        }
    }

    fun toggleShowOriginal(messageId: String) {
        _translationStateMap.update { current ->
            val existing = current[messageId]
            if (existing is MessageTranslationState.Translated) {
                current + (messageId to existing.copy(showOriginal = !existing.showOriginal))
            } else {
                current
            }
        }
    }

    fun downloadTranslationModel() {
        val modelRepo = translationModelRepository ?: return
        viewModelScope.launch {
            modelRepo.downloadAndInstallModel()
        }
    }

    companion object {
        fun transformMessagesToUiModels(
            messages: List<MessageEntity>,
            currentUserId: String,
            translationStates: Map<String, MessageTranslationState> = emptyMap()
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

                val tState = translationStates[message.id] ?: MessageTranslationState.None
                if (tState is MessageTranslationState.Translated) {
                    try { android.util.Log.i("ChatViewModel", "[ComposeState]\nmessageId=${message.id}\ndisplayedTranslation=${tState.translatedText}") } catch (_: Throwable) {}
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
                    showTimestamp = showTimestampIds.contains(message.id),
                    translationState = tState
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
