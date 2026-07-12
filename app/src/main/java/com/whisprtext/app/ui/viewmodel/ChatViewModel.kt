package com.whisprtext.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val error: String? = null
)

class ChatViewModel(
    val conversationId: String,
    private val chatRepository: ChatRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _isLoadingOlder = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ChatUiState> = combine(
        chatRepository.getMessages(conversationId),
        _isLoading,
        _isLoadingOlder,
        _error
    ) { messages, isLoading, isLoadingOlder, error ->
        ChatUiState(messages, isLoading, isLoadingOlder, error)
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

    fun loadOlderMessages() {
        val currentMessages = uiState.value.messages
        if (currentMessages.isEmpty() || _isLoadingOlder.value) return

        viewModelScope.launch {
            _isLoadingOlder.value = true
            _error.value = null
            try {
                val oldestMessage = currentMessages.last()
                chatRepository.loadOlderMessages(conversationId, oldestMessage.createdAt)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load older messages"
            } finally {
                _isLoadingOlder.value = false
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
}
