package com.whisprtext.app

import com.whisprtext.app.data.local.AppDatabase
import com.whisprtext.app.data.local.dao.ConversationDao
import com.whisprtext.app.data.local.dao.MessageDao
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.remote.WebSocketEvent
import com.whisprtext.app.data.remote.WebSocketManager
import com.whisprtext.app.data.remote.model.MessageDto
import com.whisprtext.app.data.remote.model.ReceiptDto
import com.whisprtext.app.data.repository.ChatRepository
import com.whisprtext.app.util.NetworkMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class MessageLifecycleStatusTest {

    private val database: AppDatabase = mock()
    private val conversationDao: ConversationDao = mock()
    private val messageDao: MessageDao = mock()
    private val apiClient: ApiClient = mock()
    private val webSocketManager: WebSocketManager = mock()
    private val networkMonitor: NetworkMonitor = mock()
    private val preferencesManager: com.whisprtext.app.data.local.PreferencesManager = mock()

    private val isOnlineFlow = MutableStateFlow(true)
    private val wsEventsFlow = MutableSharedFlow<WebSocketEvent>()
    private lateinit var repository: ChatRepository

    @Before
    fun setUp() {
        whenever(database.conversationDao()).thenReturn(conversationDao)
        whenever(database.messageDao()).thenReturn(messageDao)
        whenever(networkMonitor.isOnline).thenReturn(isOnlineFlow)
        whenever(webSocketManager.events).thenReturn(wsEventsFlow)
        whenever(preferencesManager.lastSyncTime).thenReturn(flowOf(null))
        whenever(preferencesManager.userId).thenReturn(flowOf("user-current"))

        run {
            kotlinx.coroutines.runBlocking {
                whenever(messageDao.getMessagesBySyncStatus(any())).thenReturn(emptyList())
            }
        }

        repository = ChatRepository(
            database,
            apiClient,
            webSocketManager,
            networkMonitor,
            preferencesManager,
            kotlinx.coroutines.test.UnconfinedTestDispatcher()
        )
    }

    @Test
    fun testSendMessageStartsAsPendingAndChangesToSentOnSuccess() = runTest {
        val serverResponse = MessageDto(
            id = "msg-server-id",
            conversationId = "conv-123",
            senderId = "user-current",
            senderDeviceId = "dev-1",
            encryptedContent = "Hello",
            createdAt = "2026-07-12T12:00:00Z"
        )
        whenever(apiClient.sendMessage(any(), any())).thenReturn(serverResponse)

        repository.sendMessage("conv-123", "Hello", "user-current", "dev-1")
        runCurrent()

        // 1. Verify it was first inserted locally as pending
        val captor = argumentCaptor<MessageEntity>()
        verify(messageDao, atLeastOnce()).insert(captor.capture())

        val localPending = captor.allValues.firstOrNull { it.syncStatus == "pending" }
        assert(localPending != null)

        // 2. Verify it was deleted (temporary ID) and re-inserted as sent
        verify(messageDao).deleteById(localPending!!.id)
        val localSent = captor.allValues.firstOrNull { it.id == "msg-server-id" && it.syncStatus == "sent" }
        assert(localSent != null)
    }

    @Test
    fun testReceiptDeliveredChangesStatusToDelivered() = runTest {
        val existingMsg = MessageEntity(
            id = "msg-123",
            conversationId = "conv-123",
            senderId = "user-current",
            senderDeviceId = "dev-1",
            encryptedContent = "Hello",
            createdAt = 1000L,
            syncStatus = "sent"
        )
        whenever(messageDao.getById("msg-123")).thenReturn(existingMsg)

        // Emit ReceiptUpdate with status 'delivered'
        wsEventsFlow.emit(WebSocketEvent.ReceiptUpdate(
            ReceiptDto("msg-123", "user-other", "delivered", "2026-07-12T12:05:00Z")
        ))
        runCurrent()

        // Verify status was updated to delivered
        val captor = argumentCaptor<MessageEntity>()
        verify(messageDao, atLeastOnce()).insert(captor.capture())
        val updated = captor.allValues.firstOrNull { it.id == "msg-123" && it.syncStatus == "delivered" }
        assert(updated != null)
    }

    @Test
    fun testReceiptReadChangesStatusToRead() = runTest {
        val existingMsg = MessageEntity(
            id = "msg-123",
            conversationId = "conv-123",
            senderId = "user-current",
            senderDeviceId = "dev-1",
            encryptedContent = "Hello",
            createdAt = 1000L,
            syncStatus = "delivered"
        )
        whenever(messageDao.getById("msg-123")).thenReturn(existingMsg)

        // Emit ReceiptUpdate with status 'read'
        wsEventsFlow.emit(WebSocketEvent.ReceiptUpdate(
            ReceiptDto("msg-123", "user-other", "read", "2026-07-12T12:06:00Z")
        ))
        runCurrent()

        // Verify status was updated to read
        val captor = argumentCaptor<MessageEntity>()
        verify(messageDao, atLeastOnce()).insert(captor.capture())
        val updated = captor.allValues.firstOrNull { it.id == "msg-123" && it.syncStatus == "read" }
        assert(updated != null)
    }

    @Test
    fun testStatusDoesNotRegress() = runTest {
        val existingMsg = MessageEntity(
            id = "msg-123",
            conversationId = "conv-123",
            senderId = "user-current",
            senderDeviceId = "dev-1",
            encryptedContent = "Hello",
            createdAt = 1000L,
            syncStatus = "read"
        )
        whenever(messageDao.getById("msg-123")).thenReturn(existingMsg)

        // Emit a delayed 'delivered' event
        wsEventsFlow.emit(WebSocketEvent.ReceiptUpdate(
            ReceiptDto("msg-123", "user-other", "delivered", "2026-07-12T12:05:00Z")
        ))
        runCurrent()

        // Verify it was NOT updated back to delivered (no inserts with status 'delivered')
        val captor = argumentCaptor<MessageEntity>()
        verify(messageDao, never()).insert(argThat { syncStatus == "delivered" })
    }
}
