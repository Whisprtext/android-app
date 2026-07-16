package com.whisprtext.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.remote.model.MessageDto
import com.whisprtext.app.data.remote.model.ReceiptDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.*
import java.util.concurrent.TimeUnit

sealed class WebSocketEvent {
    data class NewMessage(val message: MessageDto) : WebSocketEvent()
    data class Ack(val clientMsgId: String, val messageId: String, val status: String) : WebSocketEvent()
    data class ReceiptUpdate(val receipt: ReceiptDto) : WebSocketEvent()
    data class Error(val clientMsgId: String?, val message: String) : WebSocketEvent()
    data class MessageDeleted(val messageId: String, val conversationId: String) : WebSocketEvent()
    object Connected : WebSocketEvent()
    object Disconnected : WebSocketEvent()
}

class WebSocketManager(
    private val wsUrl: String,
    private val preferencesManager: PreferencesManager,
    private val gson: Gson,
    private val client: OkHttpClient = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build(),
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) {
    companion object {
        private const val TAG = "WSManager"
    }
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(ioDispatcher)
    /** True when the WebSocket connection is established and the server has confirmed open. */
    @Volatile var isConnected = false
        private set
    private var reconnectAttempt = 0
    private var contextRef: android.content.Context? = null


    private val _events = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 100)
    val events: SharedFlow<WebSocketEvent> = _events

    init {
        scope.launch {
            preferencesManager.sessionToken.collect { token ->
                if (token != null) {
                    connect(token)
                } else {
                    disconnect()
                }
            }
        }
    }

    fun attachContext(context: android.content.Context) {
        contextRef = context
    }

    private fun startForegroundService() {
        try {
            contextRef?.let { WebSocketForegroundService.start(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start foreground service", e)
        }
    }

    private fun stopForegroundService() {
        try {
            contextRef?.let { WebSocketForegroundService.stop(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop foreground service", e)
        }
    }

    @Synchronized
    private fun connect(token: String) {
        if (isConnected) return
        
        val url = "$wsUrl?token=$token"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                reconnectAttempt = 0
                startForegroundService()
                scope.launch { _events.emit(WebSocketEvent.Connected) }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val wrapper = gson.fromJson(text, WsOutgoingEvent::class.java)
                    val event = when (wrapper.event) {
                        "new_message" -> {
                            val dataJson = gson.toJson(wrapper.data)
                            val message = gson.fromJson(dataJson, MessageDto::class.java)
                            WebSocketEvent.NewMessage(message)
                        }
                        "ack" -> {
                            val dataJson = gson.toJson(wrapper.data)
                            val ackData = gson.fromJson(dataJson, AckData::class.java)
                            WebSocketEvent.Ack(wrapper.client_msg_id ?: "", ackData.message_id, ackData.status)
                        }
                        "receipt_update" -> {
                            val dataJson = gson.toJson(wrapper.data)
                            val receipt = gson.fromJson(dataJson, ReceiptDto::class.java)
                            WebSocketEvent.ReceiptUpdate(receipt)
                        }
                        "error" -> {
                            val dataJson = gson.toJson(wrapper.data)
                            val errData = gson.fromJson(dataJson, ErrorData::class.java)
                            WebSocketEvent.Error(wrapper.client_msg_id, errData.message)
                        }
                        "message_deleted" -> {
                            val dataJson = gson.toJson(wrapper.data)
                            val map = gson.fromJson(dataJson, Map::class.java)
                            val messageId = map["message_id"] as? String ?: ""
                            val conversationId = map["conversation_id"] as? String ?: ""
                            WebSocketEvent.MessageDeleted(messageId, conversationId)
                        }
                        else -> null
                    }
                    if (event != null) {
                        scope.launch { _events.emit(event) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (webSocket === ws) {
                    handleDisconnect()
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (webSocket === ws) {
                    handleDisconnect()
                }
            }
        })
    }

    private fun handleDisconnect() {
        isConnected = false
        stopForegroundService()
        scope.launch {
            _events.emit(WebSocketEvent.Disconnected)
            delay(getBackoffDelay())
            preferencesManager.sessionToken.firstOrNull()?.let { token ->
                connect(token)
            }
        }
    }

    private fun getBackoffDelay(): Long {
        reconnectAttempt++
        val delay = (reconnectAttempt * 2000L).coerceAtMost(30000L)
        return delay
    }

    /**
     * Forces an immediate reconnection attempt, resetting the backoff counter.
     * Called when the app returns to foreground.
     */
    @Synchronized
    fun forceReconnect() {
        reconnectAttempt = 0
        webSocket?.close(1000, "Forcing reconnect")
        webSocket = null
        isConnected = false
        scope.launch {
            preferencesManager.sessionToken.firstOrNull()?.let { token ->
                connect(token)
            }
        }
    }

    @Synchronized
    fun disconnect() {
        webSocket?.close(1000, "User logged out")
        webSocket = null
        isConnected = false
        reconnectAttempt = 0
        stopForegroundService()
    }

    fun markMessageDelivered(msgId: String) {
        val payload = mapOf(
            "action" to "mark_delivered",
            "message_id" to msgId
        )
        webSocket?.send(gson.toJson(payload))
    }

    fun markMessageRead(msgId: String) {
        val payload = mapOf(
            "action" to "mark_read",
            "message_id" to msgId
        )
        webSocket?.send(gson.toJson(payload))
    }

    fun markConversationRead(convId: String) {
        val payload = mapOf(
            "action" to "mark_read",
            "conversation_id" to convId
        )
        webSocket?.send(gson.toJson(payload))
    }

    private data class WsOutgoingEvent(
        val event: String,
        val client_msg_id: String?,
        val data: Any
    )

    private data class AckData(
        val message_id: String,
        val status: String
    )

    private data class ErrorData(
        val message: String
    )
}
