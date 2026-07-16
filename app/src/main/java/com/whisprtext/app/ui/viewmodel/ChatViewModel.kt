package com.whisprtext.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.model.AppearanceSettings
import com.whisprtext.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.whisprtext.app.data.remote.model.UserDto

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val conversation: ConversationEntity? = null,
    val otherUser: UserDto? = null,
    val appearanceSettings: AppearanceSettings = AppearanceSettings()
)
 
class ChatViewModel(
    val conversationId: String,
    private val chatRepository: ChatRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
 
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _otherUser = MutableStateFlow<UserDto?>(null)
 
    val uiState: StateFlow<ChatUiState> = combine(
        chatRepository.getMessages(conversationId),
        chatRepository.getConversationFlow(conversationId),
        _otherUser,
        _isLoading,
        _error,
        preferencesManager.appearanceSettings
    ) { flowResults ->
        val messages = flowResults[0] as List<MessageEntity>
        val conversation = flowResults[1] as? ConversationEntity
        val otherUser = flowResults[2] as? UserDto
        val isLoading = flowResults[3] as Boolean
        val error = flowResults[4] as? String
        val appearance = flowResults[5] as AppearanceSettings

        ChatUiState(
            messages = messages,
            isLoading = isLoading,
            error = error,
            conversation = conversation,
            otherUser = otherUser,
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
        viewModelScope.launch {
            chatRepository.getConversationFlow(conversationId).collect { conversation ->
                if (conversation != null && conversation.type == "direct" && !conversation.username.isNullOrEmpty()) {
                    try {
                        val user = chatRepository.searchUserByUsername(conversation.username)
                        _otherUser.value = user
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
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
            _isLoading.value = true
            _error.value = null
            try {
                chatRepository.deleteMessage(messageId, forEveryone)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete message"
            } finally {
                _isLoading.value = false
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
            val senderId = _currentUserId.value
            if (senderId.isNotBlank()) {
                _isLoading.value = true
                _error.value = null
                try {
                    chatRepository.sendMediaMessage(
                        conversationId, uriString, mimeType, senderId, "android-device",
                        content = content, extraUris = extraUris
                    )
                } catch (e: Exception) {
                    _error.value = e.message ?: "Failed to send media file"
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    suspend fun getDecryptedFilePath(message: MessageEntity): String? {
        return chatRepository.downloadAndDecryptMedia(message)
    }
}
