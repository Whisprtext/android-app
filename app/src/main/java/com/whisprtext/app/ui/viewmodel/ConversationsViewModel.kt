package com.whisprtext.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ConversationsUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val username: String? = null,
    val avatarUrl: String? = null
)

class ConversationsViewModel(
    private val chatRepository: ChatRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ConversationsUiState> = combine(
        chatRepository.getConversations(),
        preferencesManager.username,
        preferencesManager.avatarUrl,
        _isLoading,
        _error
    ) { conversations, username, avatarUrl, isLoading, error ->
        ConversationsUiState(conversations, isLoading, error, username, avatarUrl)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConversationsUiState(isLoading = true)
    )

    init {
        sync()
        fetchMyProfile()
    }

    private fun fetchMyProfile() {
        viewModelScope.launch {
            try {
                val me = chatRepository.getMe()
                preferencesManager.saveAvatarUrl(me.user.avatarUrl)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
                if (members.isNotEmpty()) {
                    chatRepository.createDirectConversation(members.first(), null)
                } else {
                    chatRepository.createConversation("direct", members)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create conversation"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteConversations(conversationIds: List<String>) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                chatRepository.deleteConversations(conversationIds)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete conversations"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                chatRepository.deleteAllConversations()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete all conversations"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
