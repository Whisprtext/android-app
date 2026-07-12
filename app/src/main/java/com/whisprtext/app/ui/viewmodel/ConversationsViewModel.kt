package com.whisprtext.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ConversationsUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ConversationsViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ConversationsUiState> = combine(
        chatRepository.getConversations(),
        _isLoading,
        _error
    ) { conversations, isLoading, error ->
        ConversationsUiState(conversations, isLoading, error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConversationsUiState(isLoading = true)
    )

    init {
        sync()
    }

    fun sync() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                chatRepository.syncConversations()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to sync conversations"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createConversation(members: List<String>) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                chatRepository.createConversation("direct", members)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create conversation"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
