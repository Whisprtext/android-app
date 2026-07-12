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

    val phoneNumber = MutableStateFlow<String?>(null)
    val discoverableByUsername = MutableStateFlow(true)
    val discoverableByPhone = MutableStateFlow(true)

    private val _updateStatus = MutableSharedFlow<String>()
    val updateStatus: SharedFlow<String> = _updateStatus

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
        fetchSettings()
    }

    fun fetchSettings() {
        viewModelScope.launch {
            try {
                val response = apiClient.getMe()
                phoneNumber.value = response.user.phoneNumber
                discoverableByUsername.value = response.user.discoverableByUsername
                discoverableByPhone.value = response.user.discoverableByPhone
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveSettings(phone: String?, discUser: Boolean, discPhone: Boolean) {
        viewModelScope.launch {
            try {
                apiClient.updateSettings(
                    phoneNumber = if (phone.isNullOrBlank()) null else phone,
                    discoverableByUsername = discUser,
                    discoverableByPhone = discPhone
                )
                phoneNumber.value = phone
                discoverableByUsername.value = discUser
                discoverableByPhone.value = discPhone
                _updateStatus.emit("Settings saved successfully")
            } catch (e: Exception) {
                e.printStackTrace()
                _updateStatus.emit("Failed to save settings: ${e.localizedMessage}")
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
