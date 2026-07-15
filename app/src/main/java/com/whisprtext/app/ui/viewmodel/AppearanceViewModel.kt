package com.whisprtext.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.model.AppearanceSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppearanceViewModel(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val appearanceSettings: StateFlow<AppearanceSettings> = preferencesManager.appearanceSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppearanceSettings()
        )

    fun updatePreset(presetId: String) {
        viewModelScope.launch {
            val current = appearanceSettings.value
            preferencesManager.saveAppearanceSettings(current.copy(presetId = presetId))
        }
    }

    fun updateDoodles(enabled: Boolean) {
        viewModelScope.launch {
            val current = appearanceSettings.value
            preferencesManager.saveAppearanceSettings(current.copy(useDoodles = enabled))
        }
    }

    fun updateDoodleStyle(style: Int) {
        viewModelScope.launch {
            val current = appearanceSettings.value
            preferencesManager.saveAppearanceSettings(current.copy(doodleStyle = style))
        }
    }
}
