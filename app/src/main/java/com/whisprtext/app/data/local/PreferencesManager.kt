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
import kotlinx.coroutines.flow.distinctUntilChanged

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "whisprtext_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_SESSION_TOKEN = stringPreferencesKey("session_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_AVATAR_URL = stringPreferencesKey("avatar_url")
        private val KEY_BIO = stringPreferencesKey("bio")
        private val KEY_PHONE_NUMBER = stringPreferencesKey("phone_number")
        private val KEY_PHONE_VISIBILITY = stringPreferencesKey("phone_number_visibility")
        private val KEY_DISCOVERABLE_USERNAME = stringPreferencesKey("discoverable_by_username")
        private val KEY_DISCOVERABLE_PHONE = stringPreferencesKey("discoverable_by_phone")
        private val KEY_LAST_SYNC_TIME = stringPreferencesKey("last_sync_time")
        private val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
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
    }.distinctUntilChanged()

    suspend fun saveAppearanceSettings(settings: AppearanceSettings) {
        context.dataStore.edit { preferences ->
            preferences[KEY_APPEARANCE_SETTINGS] = gson.toJson(settings)
        }
    }

    val sessionToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_SESSION_TOKEN]
    }.distinctUntilChanged()

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

    /** Server-assigned device UUID (from login/signup). Used for Signal addressing and E2EE routing. */
    val deviceId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DEVICE_ID]
    }

    suspend fun saveDeviceId(deviceId: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEVICE_ID] = deviceId
        }
    }

    suspend fun getDeviceId(): String? = deviceId.first()

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

    val bio: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_BIO]
    }

    val phoneNumber: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_PHONE_NUMBER]
    }

    val phoneNumberVisibility: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_PHONE_VISIBILITY]
    }

    val discoverableByUsername: Flow<Boolean?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DISCOVERABLE_USERNAME]?.toBooleanStrictOrNull()
    }

    val discoverableByPhone: Flow<Boolean?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DISCOVERABLE_PHONE]?.toBooleanStrictOrNull()
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

    suspend fun saveSession(
        token: String,
        userId: String,
        username: String,
        displayName: String? = null,
        avatarUrl: String? = null,
        deviceId: String? = null
    ) {
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
            if (!deviceId.isNullOrBlank()) {
                preferences[KEY_DEVICE_ID] = deviceId
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
            if (avatarUrl.isBlank()) {
                preferences.remove(KEY_AVATAR_URL)
            } else {
                preferences[KEY_AVATAR_URL] = avatarUrl
            }
        }
    }

    suspend fun clearAvatarUrl() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_AVATAR_URL)
        }
    }

    /**
     * Persist the signed-in user's full profile for instant local loads.
     * Call after any successful profile/avatar/privacy mutation.
     */
    suspend fun saveOwnProfile(
        userId: String? = null,
        username: String,
        displayName: String,
        bio: String,
        avatarUrl: String,
        phoneNumber: String? = null,
        phoneNumberVisibility: String = "everyone",
        discoverableByUsername: Boolean = true,
        discoverableByPhone: Boolean = true
    ) {
        context.dataStore.edit { preferences ->
            if (userId != null) preferences[KEY_USER_ID] = userId
            preferences[KEY_USERNAME] = username
            preferences[KEY_DISPLAY_NAME] = displayName
            preferences[KEY_BIO] = bio
            if (avatarUrl.isBlank()) {
                preferences.remove(KEY_AVATAR_URL)
            } else {
                preferences[KEY_AVATAR_URL] = avatarUrl
            }
            if (phoneNumber.isNullOrBlank()) {
                preferences.remove(KEY_PHONE_NUMBER)
            } else {
                preferences[KEY_PHONE_NUMBER] = phoneNumber
            }
            preferences[KEY_PHONE_VISIBILITY] = phoneNumberVisibility
            preferences[KEY_DISCOVERABLE_USERNAME] = discoverableByUsername.toString()
            preferences[KEY_DISCOVERABLE_PHONE] = discoverableByPhone.toString()
        }
    }

    /** Rebuild a UserDto-like snapshot from cached preference fields (null if no username). */
    suspend fun getOwnProfileSnapshot(): OwnProfileSnapshot? {
        val prefs = context.dataStore.data.first()
        val username = prefs[KEY_USERNAME] ?: return null
        val userId = prefs[KEY_USER_ID] ?: return null
        return OwnProfileSnapshot(
            id = userId,
            username = username,
            displayName = prefs[KEY_DISPLAY_NAME] ?: "",
            bio = prefs[KEY_BIO] ?: "",
            avatarUrl = prefs[KEY_AVATAR_URL] ?: "",
            phoneNumber = prefs[KEY_PHONE_NUMBER],
            phoneNumberVisibility = prefs[KEY_PHONE_VISIBILITY] ?: "everyone",
            discoverableByUsername = prefs[KEY_DISCOVERABLE_USERNAME]?.toBooleanStrictOrNull() ?: true,
            discoverableByPhone = prefs[KEY_DISCOVERABLE_PHONE]?.toBooleanStrictOrNull() ?: true
        )
    }

    data class OwnProfileSnapshot(
        val id: String,
        val username: String,
        val displayName: String,
        val bio: String,
        val avatarUrl: String,
        val phoneNumber: String?,
        val phoneNumberVisibility: String,
        val discoverableByUsername: Boolean,
        val discoverableByPhone: Boolean
    )

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
            preferences.remove(KEY_BIO)
            preferences.remove(KEY_PHONE_NUMBER)
            preferences.remove(KEY_PHONE_VISIBILITY)
            preferences.remove(KEY_DISCOVERABLE_USERNAME)
            preferences.remove(KEY_DISCOVERABLE_PHONE)
            preferences.remove(KEY_LAST_SYNC_TIME)
            // Keep KEY_DEVICE_NAME and KEY_DEVICE_ID across re-login on same install so
            // local Signal identity stays aligned with the same logical device when possible.
        }
    }

    suspend fun getSecureValue(key: String): String? {
        val prefKey = stringPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] }.first()
    }

    suspend fun saveSecureValue(key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        context.dataStore.edit { it[prefKey] = value }
    }
}
