package com.whisprtext.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val conversation: ConversationEntity? = null
)

class ChatViewModel(
    val conversationId: String,
    private val chatRepository: ChatRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ChatUiState> = combine(
        chatRepository.getMessages(conversationId),
        chatRepository.getConversationFlow(conversationId),
        _isLoading,
        _error
    ) { messages, conversation, isLoading, error ->
        ChatUiState(
            messages = messages,
            isLoading = isLoading,
            error = error,
            conversation = conversation
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState(isLoading = true)
    )

    private val _currentUserId = MutableStateFlow("")
    val currentUserId: StateFlow<String> = _currentUserId

    init {
        viewModelScope.launch {
            preferencesManager.userId.collect { id ->
                _currentUserId.value = id ?: ""
            }
        }
        viewModelScope.launch {
            chatRepository.getMessages(conversationId).collect {
                chatRepository.markConversationAsRead(conversationId)
            }
        }
        sync()
    }

    fun sync() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                chatRepository.syncMessages(conversationId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to sync messages"
            } finally {
                _isLoading.value = false
            }
        }
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
}
