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
import com.whisprtext.app.data.repository.ContactRepository
import com.whisprtext.app.util.NetworkMonitor
import com.whisprtext.app.util.StartupInitializer
import com.whisprtext.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WhisprTextApp : Application() {
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(applicationContext) }
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "whisprtext_db"
        ).fallbackToDestructiveMigration().build()
    }
    val apiClient: ApiClient by lazy {
        ApiClient("http://127.0.0.1:8080", preferencesManager)
    }
    val webSocketManager: WebSocketManager by lazy {
        val wsUrl = "ws://127.0.0.1:8080/ws"
        val gson = com.google.gson.GsonBuilder()
            .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        WebSocketManager(wsUrl, preferencesManager, gson).also {
            it.attachContext(applicationContext)
        }
    }
    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(applicationContext) }
    val notificationHelper: NotificationHelper by lazy { NotificationHelper(applicationContext) }
    val contactRepository: ContactRepository by lazy { ContactRepository(applicationContext) }
    val chatRepository: ChatRepository by lazy {
        ChatRepository(database, apiClient, webSocketManager, networkMonitor, preferencesManager, applicationContext)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        
        // Initialise Firebase as recommended on main thread
        try {
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "FirebaseApp initialised")
        } catch (e: Exception) {
            Log.d(TAG, "FirebaseApp already initialised or init skipped: ${e.message}")
        }

        // Offload remaining non-critical initialization to background
        appScope.launch {
            initTokenRegistration()
            
            launch(Dispatchers.IO) {
                // Triggering lazy properties to warm up resources in background
                val db = database
                val repo = chatRepository
                contactRepository.loadContacts()
                
                // General startup initialization (fonts, database warmup, images)
                StartupInitializer.initialize(applicationContext, db, this)
            }
        }
    }

    private fun initTokenRegistration() {
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

    companion object {
        private const val TAG = "WhisprTextApp"
    }
}
