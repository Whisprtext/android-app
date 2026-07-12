package com.whisprtext.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.remote.ApiClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val apiClient: ApiClient,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username

    private val _userId = MutableStateFlow("")
    val userId: StateFlow<String> = _userId

    init {
        viewModelScope.launch {
            preferencesManager.username.collect { name ->
                _username.value = name ?: ""
            }
        }
        viewModelScope.launch {
            preferencesManager.userId.collect { id ->
                _userId.value = id ?: ""
            }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                apiClient.logout()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                preferencesManager.clearSession()
                onSuccess()
            }
        }
    }
}
