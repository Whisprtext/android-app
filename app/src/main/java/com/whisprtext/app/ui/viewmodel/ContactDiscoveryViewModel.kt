package com.whisprtext.app.ui.viewmodel

import android.content.Context
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.remote.model.UserDto
import com.whisprtext.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class ContactItem(val name: String, val phoneNumber: String)

class ContactDiscoveryViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _usernameSearchQuery = MutableStateFlow("")
    val usernameSearchQuery: StateFlow<String> = _usernameSearchQuery

    private val _searchResult = MutableStateFlow<UserDto?>(null)
    val searchResult: StateFlow<UserDto?> = _searchResult

    private val _matchedContacts = MutableStateFlow<List<UserDto>>(emptyList())
    val matchedContacts: StateFlow<List<UserDto>> = _matchedContacts

    private val _unmatchedContacts = MutableStateFlow<List<ContactItem>>(emptyList())
    val unmatchedContacts: StateFlow<List<ContactItem>> = _unmatchedContacts

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error

    private val _chatCreated = MutableSharedFlow<ConversationEntity>()
    val chatCreated: SharedFlow<ConversationEntity> = _chatCreated

    fun searchUser(username: String) {
        if (username.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _searchResult.value = null
            try {
                val user = chatRepository.searchUserByUsername(username)
                _searchResult.value = user
            } catch (e: Exception) {
                e.printStackTrace()
                _error.emit("User '$username' not found or is not discoverable.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun syncContacts(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val contacts = loadLocalContacts(context)
                val normalizedPhones = contacts.mapNotNull { normalizePhone(it.phoneNumber) }.distinct()

                if (normalizedPhones.isNotEmpty()) {
                    val discoverableUsers = chatRepository.lookupUsersByPhone(normalizedPhones)
                    _matchedContacts.value = discoverableUsers

                    // Filter out contacts that were matched
                    val matchedPhones = discoverableUsers.mapNotNull { it.phoneNumber }
                    val unmatched = contacts.filter { contact ->
                        val norm = normalizePhone(contact.phoneNumber)
                        norm == null || norm !in matchedPhones
                    }
                    _unmatchedContacts.value = unmatched
                } else {
                    _unmatchedContacts.value = contacts
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _error.emit("Failed to check contacts: ${e.localizedMessage}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startChat(user: UserDto) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val conversation = chatRepository.createDirectConversation(user.id, null)
                _chatCreated.emit(conversation)
            } catch (e: Exception) {
                e.printStackTrace()
                _error.emit("Failed to start chat: ${e.localizedMessage}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadLocalContacts(context: Context): List<ContactItem> {
        val list = mutableListOf<ContactItem>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (nameIndex != -1 && numberIndex != -1) {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIndex) ?: ""
                        val number = cursor.getString(numberIndex) ?: ""
                        if (number.isNotBlank()) {
                            list.add(ContactItem(name, number))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun normalizePhone(phone: String): String? {
        val trimmed = phone.trim()
        if (!trimmed.startsWith("+")) {
            val digits = trimmed.filter { it.isDigit() }
            if (digits.length >= 7) {
                return "+$digits"
            }
            return null
        }
        val digits = trimmed.substring(1).filter { it.isDigit() }
        if (digits.length >= 7) {
            return "+$digits"
        }
        return null
    }
}
