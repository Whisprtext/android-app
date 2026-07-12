package com.whisprtext.app

import android.app.Application
import androidx.room.Room
import com.google.gson.Gson
import com.whisprtext.app.data.local.AppDatabase
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.remote.WebSocketManager
import com.whisprtext.app.data.repository.ChatRepository
import com.whisprtext.app.util.NetworkMonitor

class WhisprTextApp : Application() {
    lateinit var preferencesManager: PreferencesManager
    lateinit var database: AppDatabase
    lateinit var apiClient: ApiClient
    lateinit var webSocketManager: WebSocketManager
    lateinit var networkMonitor: NetworkMonitor
    lateinit var chatRepository: ChatRepository

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
        webSocketManager = WebSocketManager(wsUrl, preferencesManager, gson)
        networkMonitor = NetworkMonitor(applicationContext)
        
        chatRepository = ChatRepository(database, apiClient, webSocketManager, networkMonitor, preferencesManager)
    }
}
