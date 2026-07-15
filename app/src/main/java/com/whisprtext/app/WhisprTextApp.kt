package com.whisprtext.app

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.whisprtext.app.data.local.AppDatabase
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.remote.WebSocketManager
import com.whisprtext.app.data.repository.ChatRepository
import com.whisprtext.app.util.NetworkMonitor
import com.whisprtext.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WhisprTextApp : Application() {
    lateinit var preferencesManager: PreferencesManager
    lateinit var database: AppDatabase
    lateinit var apiClient: ApiClient
    lateinit var webSocketManager: WebSocketManager
    lateinit var networkMonitor: NetworkMonitor
    lateinit var chatRepository: ChatRepository
    lateinit var notificationHelper: NotificationHelper

    private val appScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(applicationContext)
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "whisprtext_db"
        ).fallbackToDestructiveMigration().build()
        
        // Using 127.0.0.1:8080. Run 'adb reverse tcp:8080 tcp:8080' to forward connections from the device/emulator to host.
        apiClient = ApiClient("http://127.0.0.1:8080", preferencesManager)
        
        val wsUrl = "ws://127.0.0.1:8080/ws"
        val gson = com.google.gson.GsonBuilder()
            .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        webSocketManager = WebSocketManager(wsUrl, preferencesManager, gson).also {
            it.attachContext(applicationContext)
        }
        networkMonitor = NetworkMonitor(applicationContext)
        notificationHelper = NotificationHelper(applicationContext)
        
        chatRepository = ChatRepository(database, apiClient, webSocketManager, networkMonitor, preferencesManager, applicationContext)

        // Initialise Firebase and fetch a real FCM token.
        initFirebaseAndRegisterToken()
    }

    private fun initFirebaseAndRegisterToken() {
        try {
            // FirebaseApp.initializeApp() reads google-services.json automatically
            // if the google-services Gradle plugin is applied, or uses the values
            // provided in FirebaseOptions if configured manually.
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "FirebaseApp initialised")
        } catch (e: Exception) {
            // Firebase may already be initialised if the google-services plugin is active.
            Log.d(TAG, "FirebaseApp already initialised or init skipped: ${e.message}")
        }

        appScope.launch {
            try {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w(TAG, "Failed to get FCM token", task.exception)
                        return@addOnCompleteListener
                    }
                    val token = task.result
                    if (token != null) {
                        Log.i(TAG, "FCM token obtained: ${token.take(16)}...")
                        appScope.launch {
                            try {
                                chatRepository.registerPushToken(token)
                                Log.i(TAG, "FCM token registered with backend")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to register FCM token with backend", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching FCM token", e)
            }
        }
    }

    companion object {
        private const val TAG = "WhisprTextApp"
    }
}
