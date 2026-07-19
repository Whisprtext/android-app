package com.whisprtext.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import android.util.Log

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val apiClient: ApiClient,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun login(username: String, passwordHash: String) {
        Log.d("AuthViewModel", "login() started: username=$username")
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val devName = preferencesManager.getOrCreateDeviceName()
                Log.d("AuthViewModel", "login() making API call with device: $devName")
                val response = apiClient.login(username, passwordHash, devName)
                Log.d("AuthViewModel", "login() API call success: token=${response.sessionToken}")
                preferencesManager.saveSession(
                    response.sessionToken,
                    response.user.id,
                    response.user.username,
                    response.user.displayName,
                    response.user.avatarUrl,
                    deviceId = response.device.id
                )
                preferencesManager.saveOwnProfile(
                    userId = response.user.id,
                    username = response.user.username,
                    displayName = response.user.displayName,
                    bio = response.user.bio,
                    avatarUrl = response.user.avatarUrl,
                    phoneNumber = response.user.phoneNumber,
                    phoneNumberVisibility = response.user.phoneNumberVisibility,
                    discoverableByUsername = response.user.discoverableByUsername,
                    discoverableByPhone = response.user.discoverableByPhone
                )
                Log.d("AuthViewModel", "login() session saved successfully")
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                Log.e("AuthViewModel", "login() failed with exception", e)
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun signup(username: String, passwordHash: String) {
        Log.d("AuthViewModel", "signup() started: username=$username")
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val devName = preferencesManager.getOrCreateDeviceName()
                Log.d("AuthViewModel", "signup() making API call with device: $devName")
                val response = apiClient.signup(username, passwordHash, devName)
                Log.d("AuthViewModel", "signup() API call success: token=${response.sessionToken}")
                preferencesManager.saveSession(
                    response.sessionToken,
                    response.user.id,
                    response.user.username,
                    response.user.displayName,
                    response.user.avatarUrl,
                    deviceId = response.device.id
                )
                preferencesManager.saveOwnProfile(
                    userId = response.user.id,
                    username = response.user.username,
                    displayName = response.user.displayName,
                    bio = response.user.bio,
                    avatarUrl = response.user.avatarUrl,
                    phoneNumber = response.user.phoneNumber,
                    phoneNumberVisibility = response.user.phoneNumberVisibility,
                    discoverableByUsername = response.user.discoverableByUsername,
                    discoverableByPhone = response.user.discoverableByPhone
                )
                Log.d("AuthViewModel", "signup() session saved successfully")
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                Log.e("AuthViewModel", "signup() failed with exception", e)
                _authState.value = AuthState.Error(e.message ?: "Signup failed")
            }
        }
    }

    fun clearState() {
        Log.d("AuthViewModel", "clearState() called")
        _authState.value = AuthState.Idle
    }
}
