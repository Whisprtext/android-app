package com.whisprtext.app

import android.app.Application
import androidx.room.Room
import com.whisprtext.app.data.local.AppDatabase
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.repository.ChatRepository

class WhisprTextApp : Application() {
    lateinit var preferencesManager: PreferencesManager
    lateinit var database: AppDatabase
    lateinit var apiClient: ApiClient
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
        chatRepository = ChatRepository(database, apiClient)
    }
}
