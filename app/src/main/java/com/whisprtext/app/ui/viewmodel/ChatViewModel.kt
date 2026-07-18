package com.whisprtext.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.model.AppearanceSettings
import com.whisprtext.app.data.repository.ChatRepository
import com.whisprtext.app.data.repository.ContactRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.whisprtext.app.data.remote.model.UserDto
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
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

    val uiState: StateFlow<ChatUiState> = combine(
        chatRepository.getMessages(conversationId),
        chatRepository.getConversationFlow(conversationId),
        contactRepository.contactsMap,
        _otherUser,
        _isLoading,
        _error,
        preferencesManager.appearanceSettings
    ) { flowResults ->
        val messages = flowResults[0] as List<MessageEntity>
        val conversation = flowResults[1] as? ConversationEntity
        val contactsMap = flowResults[2] as Map<String, String>
        val otherUser = flowResults[3] as? UserDto
        val isLoading = flowResults[4] as Boolean
        val error = flowResults[5] as? String
        val appearance = flowResults[6] as AppearanceSettings

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
            messages = messages,
            contactsMap = contactsMap,
            isLoading = isLoading,
            error = error,
            conversation = conversation,
            otherUser = mergedOther,
            appearanceSettings = appearance
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState(isLoading = true)
    )

    private val _currentUserId = MutableStateFlow("")
    val currentUserId: StateFlow<String> = _currentUserId

    init {
        chatRepository.activeConversationId = conversationId
        viewModelScope.launch {
            preferencesManager.userId.collect { id ->
                _currentUserId.value = id ?: ""
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
            val senderId = _currentUserId.value
            if (senderId.isNotBlank()) {
                chatRepository.sendMessage(conversationId, content, senderId, "android-device")
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
                val senderId = _currentUserId.value
                if (senderId.isNotBlank()) {
                    chatRepository.sendMediaMessage(
                        conversationId = conversationId,
                        uriString = uriString,
                        mimeType = mimeType,
                        senderId = senderId,
                        senderDeviceId = "android-device",
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

    suspend fun getDecryptedFilePath(message: MessageEntity): String? {
        return try {
            chatRepository.downloadAndDecryptMedia(message)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
