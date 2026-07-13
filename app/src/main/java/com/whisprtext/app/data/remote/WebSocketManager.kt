package com.whisprtext.app.data.remote

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
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(ioDispatcher)
    private var isConnected = false
    private var reconnectAttempt = 0

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

    @Synchronized
    private fun connect(token: String) {
        if (isConnected) return
        
        val url = "$wsUrl?token=$token"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                reconnectAttempt = 0
                scope.launch { _events.emit(WebSocketEvent.Connected) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
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

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleDisconnect()
            }
        })
    }

    private fun handleDisconnect() {
        isConnected = false
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
        val delay = reconnectAttempt * 2000L
        return delay.coerceAtMost(30000L)
    }

    @Synchronized
    fun disconnect() {
        webSocket?.close(1000, "User logged out")
        webSocket = null
        isConnected = false
        reconnectAttempt = 0
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
