package com.whisprtext.app

import com.google.gson.Gson
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.remote.WebSocketEvent
import com.whisprtext.app.data.remote.WebSocketManager
import com.whisprtext.app.data.remote.model.MessageDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val preferencesManager: PreferencesManager = mock()
    private val client: OkHttpClient = mock()
    private val webSocket: WebSocket = mock()
    private val gson = com.google.gson.GsonBuilder()
        .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    private val sessionTokenFlow = MutableSharedFlow<String?>()

    private lateinit var webSocketManager: WebSocketManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(preferencesManager.sessionToken).thenReturn(sessionTokenFlow)
        whenever(client.newWebSocket(any(), any())).thenReturn(webSocket)
        webSocketManager = WebSocketManager(
            "ws://localhost/ws",
            preferencesManager,
            gson,
            client,
            UnconfinedTestDispatcher()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testConnectInvokesNewWebSocketAndParsesNewMessage() = runTest {
        val collectEvents = mutableListOf<WebSocketEvent>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            webSocketManager.events.collect { collectEvents.add(it) }
        }

        // Emit token to trigger connection
        sessionTokenFlow.emit("test-token-123")
        runCurrent()

        // Capture request and listener
        val requestCaptor = argumentCaptor<Request>()
        val listenerCaptor = argumentCaptor<WebSocketListener>()
        verify(client).newWebSocket(requestCaptor.capture(), listenerCaptor.capture())

        // Verify request URL has the token parameter
        assertEquals("http://localhost/ws?token=test-token-123", requestCaptor.firstValue.url.toString())

        // Trigger onOpen
        val listener = listenerCaptor.firstValue
        listener.onOpen(webSocket, mock())
        runCurrent()
        assertEquals(WebSocketEvent.Connected, collectEvents.first())

        // Trigger onMessage for "new_message" action
        val newMessageJson = """
            {
                "event": "new_message",
                "data": {
                    "id": "msg-123",
                    "conversation_id": "conv-123",
                    "sender_id": "user-abc",
                    "sender_device_id": "dev-xyz",
                    "encrypted_content": "SGVsbG8=",
                    "created_at": "2026-07-12T12:00:00Z"
                }
            }
        """.trimIndent()
        listener.onMessage(webSocket, newMessageJson)
        runCurrent()

        assertTrue(collectEvents.size >= 2)
        val secondEvent = collectEvents[1]
        assertTrue(secondEvent is WebSocketEvent.NewMessage)
        val msg = (secondEvent as WebSocketEvent.NewMessage).message
        assertEquals("msg-123", msg.id)
        assertEquals("SGVsbG8=", msg.encryptedContent)

        collectJob.cancel()
    }

    @Test
    fun testParsesAckAndErrorFrames() = runTest {
        val collectEvents = mutableListOf<WebSocketEvent>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            webSocketManager.events.collect { collectEvents.add(it) }
        }

        sessionTokenFlow.emit("test-token-123")
        runCurrent()

        val listenerCaptor = argumentCaptor<WebSocketListener>()
        verify(client).newWebSocket(any(), listenerCaptor.capture())
        val listener = listenerCaptor.firstValue

        // Trigger onMessage for "ack" action
        val ackJson = """
            {
                "event": "ack",
                "client_msg_id": "temp-123",
                "data": {
                    "message_id": "db-uuid-456",
                    "status": "sent"
                }
            }
        """.trimIndent()
        listener.onMessage(webSocket, ackJson)
        runCurrent()

        val ackEvent = collectEvents.firstOrNull { it is WebSocketEvent.Ack }
        assertTrue(ackEvent is WebSocketEvent.Ack)
        assertEquals("temp-123", (ackEvent as WebSocketEvent.Ack).clientMsgId)
        assertEquals("db-uuid-456", ackEvent.messageId)
        assertEquals("sent", ackEvent.status)

        // Trigger onMessage for "error" action
        val errorJson = """
            {
                "event": "error",
                "client_msg_id": "temp-456",
                "data": {
                    "message": "failed to persist message"
                }
            }
        """.trimIndent()
        listener.onMessage(webSocket, errorJson)
        runCurrent()

        val errEvent = collectEvents.firstOrNull { it is WebSocketEvent.Error }
        assertTrue(errEvent is WebSocketEvent.Error)
        assertEquals("temp-456", (errEvent as WebSocketEvent.Error).clientMsgId)
        assertEquals("failed to persist message", errEvent.message)

        collectJob.cancel()
    }
}
