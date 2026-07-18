package com.whisprtext.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.repository.ChatRepository
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.remote.model.UserDto
import com.whisprtext.app.util.AvatarUrlResolver
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val apiClient: ApiClient,
    private val preferencesManager: PreferencesManager,
    private val chatRepository: ChatRepository,
    val targetUsername: String? = null,
    private val appContext: Context? = null
) : ViewModel() {

    private val _userProfile = MutableStateFlow<UserDto?>(null)
    val userProfile: StateFlow<UserDto?> = _userProfile

    private val _gradientColors = MutableStateFlow<Pair<Int?, Int?>>(null to null)
    val gradientColors: StateFlow<Pair<Int?, Int?>> = _gradientColors

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isAvatarUploading = MutableStateFlow(false)
    val isAvatarUploading: StateFlow<Boolean> = _isAvatarUploading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _successMessage = MutableSharedFlow<String>()
    val successMessage: SharedFlow<String> = _successMessage

    private val usernameRegex = Regex("^[a-zA-Z0-9._]+$")
    private val displayNameRegex = Regex("^[a-zA-Z\\s]+$")
    private val phoneRegex = Regex("^\\+[0-9]{7,15}$")

    private val isOwnProfile: Boolean get() = targetUsername == null

    init {
        loadProfileLocalFirst()
    }

    /**
     * Own profile: load entirely from local cache (instant). Network only after mutations.
     * Other profile: show local cache immediately, then refresh from network (on open / avatar tap).
     */
    fun loadProfileLocalFirst() {
        viewModelScope.launch {
            _errorMessage.value = null
            if (isOwnProfile) {
                val cached = chatRepository.getCachedSelfProfile()
                if (cached != null) {
                    _userProfile.value = cached
                    loadOwnGradients(cached.id)
                    _isLoading.value = false
                    // Always refresh own profile from network when opening
                    refreshOwnProfile()
                    return@launch
                }
                // First run / empty cache: one network fetch to seed local storage.
                _isLoading.value = true
                refreshOwnProfile()
            } else {
                val username = targetUsername!!
                // 1) Instant local paint
                val cached = chatRepository.getCachedProfileByUsername(username)
                if (cached != null) {
                    _userProfile.value = cached
                    loadOtherGradients(username, cached.id)
                    _isLoading.value = false
                } else {
                    _isLoading.value = true
                }
                // 2) Always refresh other users when opening their profile
                refreshOtherProfile(username)
            }
        }
    }

    /** Explicit network refresh for own profile. */
    fun refreshOwnProfile() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val user = chatRepository.refreshOwnProfileFromNetwork()
                _userProfile.value = user
                loadOwnGradients(user.id)
            } catch (e: Exception) {
                e.printStackTrace()
                if (_userProfile.value == null) {
                    _errorMessage.value = "Failed to load profile: ${e.localizedMessage}"
                }
            } finally {
                _isRefreshing.value = false
                _isLoading.value = false
            }
        }
    }

    /** Explicit network refresh for another user's profile (e.g. re-tap avatar). */
    fun refreshOtherProfile(username: String? = targetUsername) {
        val target = username ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            try {
                val user = chatRepository.refreshContactProfile(target)
                _userProfile.value = user
                loadOtherGradients(target, user.id)
            } catch (e: Exception) {
                e.printStackTrace()
                // Keep cached profile visible if refresh fails.
                if (_userProfile.value == null) {
                    _errorMessage.value = "Failed to load profile: ${e.localizedMessage}"
                }
            } finally {
                _isRefreshing.value = false
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadOwnGradients(userId: String) {
        val start = preferencesManager.gradientStart.first()
        val end = preferencesManager.gradientEnd.first()
        if (start != null && end != null) {
            _gradientColors.value = start to end
        } else {
            val (s, e) = com.whisprtext.app.util.ColorGenerator.generateGradient(userId)
            preferencesManager.saveGradientColors(s, e)
            _gradientColors.value = s to e
        }
    }

    private suspend fun loadOtherGradients(username: String, userId: String) {
        val conv = chatRepository.getDirectConversationByContact(username, null)
        if (conv != null) {
            _gradientColors.value = conv.gradientStartColor to conv.gradientEndColor
        } else {
            val (start, end) = com.whisprtext.app.util.ColorGenerator.generateGradient(userId)
            _gradientColors.value = start to end
        }
    }

    fun validateUsername(username: String): Boolean {
        return username.isNotBlank() && username.matches(usernameRegex)
    }

    fun validateDisplayName(displayName: String): Boolean {
        return displayName.isNotBlank() && displayName.matches(displayNameRegex)
    }

    fun validatePhone(phone: String?): Boolean {
        if (phone.isNullOrBlank()) return true
        return phone.matches(phoneRegex)
    }

    fun saveProfile(username: String, displayName: String, bio: String) {
        if (!validateUsername(username)) {
            _errorMessage.value = "Invalid username format. Only letters, numbers, dot (.), and underscore (_) are allowed."
            return
        }
        if (!validateDisplayName(displayName)) {
            _errorMessage.value = "Invalid display name. Only alphabets and spaces are allowed."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val updatedUser = chatRepository.updateProfile(
                    username = username.trim(),
                    displayName = displayName.trim(),
                    bio = bio.trim()
                )
                _userProfile.value = updatedUser
                _successMessage.emit("Profile updated successfully")
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = e.localizedMessage ?: "Failed to update profile"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadAvatar(jpegBytes: ByteArray, onComplete: (Boolean) -> Unit = {}) {
        if (_isAvatarUploading.value) {
            onComplete(false)
            return
        }
        if (jpegBytes.isEmpty()) {
            _errorMessage.value = "Empty image data"
            onComplete(false)
            return
        }
        if (jpegBytes.size > 2 * 1024 * 1024) {
            _errorMessage.value = "Avatar must be under 2MB. Try a smaller crop."
            onComplete(false)
            return
        }

        viewModelScope.launch {
            _isAvatarUploading.value = true
            _errorMessage.value = null
            val previousAvatar = _userProfile.value?.avatarUrl
            try {
                val mime = "image/jpeg"
                val init = apiClient.initAvatarUpload(mime, jpegBytes.size.toLong())
                val uploaded = apiClient.uploadAvatarFile(init.uploadUrl, jpegBytes, mime)
                if (!uploaded) {
                    throw Exception("Upload failed. Check your connection and try again.")
                }
                // setAvatar caches locally + updates conversations via ChatRepository
                val updatedUser = chatRepository.setAvatar(
                    fileId = init.fileId,
                    fileUrl = init.fileUrl,
                    mimeType = mime,
                    sizeBytes = jpegBytes.size.toLong()
                )
                applyAvatarStateChange(previousAvatar, updatedUser)
                _successMessage.emit("Profile photo updated")
                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = when {
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                        e.message?.contains("failed to connect", ignoreCase = true) == true ||
                        e.message?.contains("timeout", ignoreCase = true) == true ->
                        "Network error. Your photo was not changed."
                    else -> e.localizedMessage ?: "Failed to upload profile photo"
                }
                onComplete(false)
            } finally {
                _isAvatarUploading.value = false
            }
        }
    }

    fun removeAvatar() {
        if (_isAvatarUploading.value) return
        viewModelScope.launch {
            _isAvatarUploading.value = true
            _errorMessage.value = null
            val previousAvatar = _userProfile.value?.avatarUrl
            try {
                val updatedUser = chatRepository.removeAvatar()
                applyAvatarStateChange(previousAvatar, updatedUser)
                _successMessage.emit("Profile photo removed")
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = e.localizedMessage ?: "Failed to remove profile photo"
            } finally {
                _isAvatarUploading.value = false
            }
        }
    }

    private suspend fun applyAvatarStateChange(previousAvatar: String?, updatedUser: UserDto) {
        val ctx = appContext
        if (!previousAvatar.isNullOrBlank() && previousAvatar != updatedUser.avatarUrl) {
            if (ctx != null) {
                AvatarUrlResolver.evictFromImageLoader(ctx, previousAvatar)
            }
            AvatarUrlResolver.invalidate(previousAvatar)
        }
        if (updatedUser.avatarUrl.isNotBlank() && ctx != null) {
            AvatarUrlResolver.evictFromImageLoader(ctx, updatedUser.avatarUrl)
        }
        AvatarUrlResolver.invalidate(updatedUser.avatarUrl.takeIf { it.isNotBlank() })
        _userProfile.value = updatedUser
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
                val updatedUser = chatRepository.updateSettings(
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

    fun clearError() {
        _errorMessage.value = null
    }
}
