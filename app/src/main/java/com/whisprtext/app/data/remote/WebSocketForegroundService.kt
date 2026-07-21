package com.whisprtext.app.data.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.whisprtext.app.WhisprTextApp

class WebSocketForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: starting foreground")
        createNotificationChannel()
        promoteToForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ensuring foreground")
        promoteToForeground()
        val app = application as? WhisprTextApp
        if (app?.webSocketManager != null) {
            Log.d(TAG, "Foreground service active, WebSocket keepalive ensured")
        }
        return START_STICKY
    }

    private fun promoteToForeground() {
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "Successfully promoted to foreground")
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) {
                Log.e(TAG, "Foreground service start not allowed from background. " +
                        "This can happen if the app is restricted or battery optimized.", e)
            } else {
                Log.e(TAG, "Failed to start foreground: ${e.message}", e)
            }
            // If we failed to start foreground on Android 12+, we should probably stop ourselves
            // to avoid the system killing us or throwing an exception later.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Foreground service destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WhisprText Connection",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps messaging connection alive"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("WhisprText")
            .setContentText("Connected")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "WSForegroundService"
        private const val CHANNEL_ID = "whisprtext_connection"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, WebSocketForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WebSocketForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
