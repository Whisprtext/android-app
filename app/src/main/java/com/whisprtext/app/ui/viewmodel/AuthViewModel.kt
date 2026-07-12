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

    fun login(username: String, passwordHash: String, deviceName: String) {
        Log.d("AuthViewModel", "login() started: username=$username, deviceName=$deviceName")
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                Log.d("AuthViewModel", "login() making API call to login")
                val response = apiClient.login(username, passwordHash, deviceName)
                Log.d("AuthViewModel", "login() API call success: token=${response.sessionToken}")
                preferencesManager.saveSession(response.sessionToken, response.user.id, response.user.username)
                Log.d("AuthViewModel", "login() session saved successfully")
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                Log.e("AuthViewModel", "login() failed with exception", e)
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun signup(username: String, passwordHash: String, deviceName: String) {
        Log.d("AuthViewModel", "signup() started: username=$username, deviceName=$deviceName")
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                Log.d("AuthViewModel", "signup() making API call to signup")
                val response = apiClient.signup(username, passwordHash, deviceName)
                Log.d("AuthViewModel", "signup() API call success: token=${response.sessionToken}")
                preferencesManager.saveSession(response.sessionToken, response.user.id, response.user.username)
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
