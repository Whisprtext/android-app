package com.whisprtext.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.repository.ChatRepository
import com.whisprtext.app.data.repository.ContactRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ConversationsUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val contactsMap: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val gradientStart: Int? = null,
    val gradientEnd: Int? = null
)

class ConversationsViewModel(
    private val chatRepository: ChatRepository,
    private val contactRepository: ContactRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ConversationsUiState> = combine(
        chatRepository.getConversations(),
        contactRepository.contactsMap,
        preferencesManager.username,
        preferencesManager.displayName,
        preferencesManager.avatarUrl,
        preferencesManager.gradientStart,
        preferencesManager.gradientEnd,
        _isLoading,
        _error
    ) { args ->
        ConversationsUiState(
            conversations = args[0] as List<ConversationEntity>,
            contactsMap = args[1] as Map<String, String>,
            username = args[2] as? String,
            displayName = args[3] as? String,
            avatarUrl = args[4] as? String,
            gradientStart = args[5] as? Int,
            gradientEnd = args[6] as? Int,
            isLoading = args[7] as Boolean,
            error = args[8] as? String
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConversationsUiState(isLoading = true)
    )

    init {
        sync()
        ensureLocalOwnProfile()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            chatRepository.getConversations().collect { conversations ->
                conversations.forEach { conv ->
                    chatRepository.preloadMessages(conv.id)
                }
            }
        }
    }

    /**
     * Use local own-profile cache only. Network refresh happens after mutations
     * (profile save / avatar upload), not on every conversations screen open.
     * Seeds from network only if local cache is empty (first launch after login).
     */
    private fun ensureLocalOwnProfile() {
        viewModelScope.launch {
            try {
                val cached = chatRepository.getCachedSelfProfile()
                if (cached != null) {
                    val currentStart = preferencesManager.gradientStart.first()
                    if (currentStart == null) {
                        val (start, end) = com.whisprtext.app.util.ColorGenerator.generateGradient(cached.id)
                        preferencesManager.saveGradientColors(start, end)
                    }
                    return@launch
                }
                // Empty cache — one-time seed so avatars/names appear after reinstall/login.
                chatRepository.refreshOwnProfileFromNetwork()
                val userId = preferencesManager.userId.first()
                if (userId != null) {
                    val currentStart = preferencesManager.gradientStart.first()
                    if (currentStart == null) {
                        val (start, end) = com.whisprtext.app.util.ColorGenerator.generateGradient(userId)
                        preferencesManager.saveGradientColors(start, end)
                    }
                }
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
