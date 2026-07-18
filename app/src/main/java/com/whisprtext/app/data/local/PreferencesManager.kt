package com.whisprtext.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.whisprtext.app.data.model.AppearanceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "whisprtext_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_SESSION_TOKEN = stringPreferencesKey("session_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_AVATAR_URL = stringPreferencesKey("avatar_url")
        private val KEY_LAST_SYNC_TIME = stringPreferencesKey("last_sync_time")
        private val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
        private val KEY_PUSH_TOKEN = stringPreferencesKey("push_token")
        private val KEY_APPEARANCE_SETTINGS = stringPreferencesKey("appearance_settings")
        private val KEY_GRADIENT_START = intPreferencesKey("gradient_start")
        private val KEY_GRADIENT_END = intPreferencesKey("gradient_end")
    }

    private val gson = Gson()

    val appearanceSettings: Flow<AppearanceSettings> = context.dataStore.data.map { preferences ->
        val json = preferences[KEY_APPEARANCE_SETTINGS]
        if (json != null) {
            try {
                gson.fromJson(json, AppearanceSettings::class.java)
            } catch (e: Exception) {
                AppearanceSettings()
            }
        } else {
            AppearanceSettings()
        }
    }

    suspend fun saveAppearanceSettings(settings: AppearanceSettings) {
        context.dataStore.edit { preferences ->
            preferences[KEY_APPEARANCE_SETTINGS] = gson.toJson(settings)
        }
    }

    val sessionToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_SESSION_TOKEN]
    }

    val deviceName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DEVICE_NAME]
    }

    suspend fun getOrCreateDeviceName(): String {
        val existing = deviceName.first()
        if (!existing.isNullOrEmpty()) {
            return existing
        }
        val newName = "Android Device " + java.util.UUID.randomUUID().toString().take(6)
        context.dataStore.edit { preferences ->
            preferences[KEY_DEVICE_NAME] = newName
        }
        return newName
    }

    val userId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_ID]
    }

    val username: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_USERNAME]
    }

    val displayName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DISPLAY_NAME]
    }

    val avatarUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_AVATAR_URL]
    }

    val lastSyncTime: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_SYNC_TIME]
    }

    val pushToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_PUSH_TOKEN]
    }

    val gradientStart: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[KEY_GRADIENT_START]
    }

    val gradientEnd: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[KEY_GRADIENT_END]
    }

    suspend fun saveGradientColors(start: Int, end: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_GRADIENT_START] = start
            preferences[KEY_GRADIENT_END] = end
        }
    }

    suspend fun savePushToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PUSH_TOKEN] = token
        }
    }

    suspend fun saveSession(token: String, userId: String, username: String, displayName: String? = null, avatarUrl: String? = null) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SESSION_TOKEN] = token
            preferences[KEY_USER_ID] = userId
            preferences[KEY_USERNAME] = username
            if (displayName != null) {
                preferences[KEY_DISPLAY_NAME] = displayName
            }
            if (avatarUrl != null) {
                preferences[KEY_AVATAR_URL] = avatarUrl
            }
        }
    }

    suspend fun saveUsername(username: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USERNAME] = username
        }
    }

    suspend fun saveDisplayName(displayName: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DISPLAY_NAME] = displayName
        }
    }

    suspend fun saveAvatarUrl(avatarUrl: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AVATAR_URL] = avatarUrl
        }
    }

    suspend fun saveLastSyncTime(time: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_SYNC_TIME] = time
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_SESSION_TOKEN)
            preferences.remove(KEY_USER_ID)
            preferences.remove(KEY_USERNAME)
            preferences.remove(KEY_DISPLAY_NAME)
            preferences.remove(KEY_AVATAR_URL)
            preferences.remove(KEY_LAST_SYNC_TIME)
        }
    }
}
