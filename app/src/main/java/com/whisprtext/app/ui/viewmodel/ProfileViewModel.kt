package com.whisprtext.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.remote.model.UserDto
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val apiClient: ApiClient,
    private val preferencesManager: PreferencesManager,
    val targetUsername: String? = null
) : ViewModel() {

    private val _userProfile = MutableStateFlow<UserDto?>(null)
    val userProfile: StateFlow<UserDto?> = _userProfile

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _successMessage = MutableSharedFlow<String>()
    val successMessage: SharedFlow<String> = _successMessage

    // Validations regex
    private val usernameRegex = Regex("^[a-zA-Z0-9._]+$")
    private val phoneRegex = Regex("^\\+[0-9]{7,15}$")

    init {
        fetchProfile()
    }

    fun fetchProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                if (targetUsername != null) {
                    val user = apiClient.searchUserByUsername(targetUsername)
                    _userProfile.value = user
                } else {
                    val me = apiClient.getMe()
                    _userProfile.value = me.user
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to load profile: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun validateUsername(username: String): Boolean {
        return username.isNotBlank() && username.matches(usernameRegex)
    }

    fun validatePhone(phone: String?): Boolean {
        if (phone.isNullOrBlank()) return true // Phone is optional
        return phone.matches(phoneRegex)
    }

    fun saveProfile(username: String, displayName: String, bio: String, avatarUrl: String) {
        if (!validateUsername(username)) {
            _errorMessage.value = "Invalid username format. Only letters, numbers, dot (.), and underscore (_) are allowed."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val updatedUser = apiClient.updateProfile(
                    username = username.trim(),
                    displayName = displayName.trim(),
                    bio = bio.trim(),
                    avatarUrl = avatarUrl.trim()
                )
                _userProfile.value = updatedUser
                preferencesManager.saveUsername(updatedUser.username)
                _successMessage.emit("Profile updated successfully")
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = e.localizedMessage ?: "Failed to update profile"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun savePrivacySettings(
        phoneNumber: String?,
        discoverableByUsername: Boolean,
        discoverableByPhone: Boolean,
        phoneNumberVisibility: String
    ) {
        if (!validatePhone(phoneNumber)) {
            _errorMessage.value = "Invalid phone number format. Must match international format (e.g., +15555551234)."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val cleanPhone = if (phoneNumber.isNullOrBlank()) null else phoneNumber.trim()
                val updatedUser = apiClient.updateSettings(
                    phoneNumber = cleanPhone,
                    discoverableByUsername = discoverableByUsername,
                    discoverableByPhone = discoverableByPhone,
                    displayName = _userProfile.value?.displayName,
                    phoneNumberVisibility = phoneNumberVisibility
                )
                _userProfile.value = updatedUser
                _successMessage.emit("Privacy settings saved")
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = e.localizedMessage ?: "Failed to update privacy settings"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
