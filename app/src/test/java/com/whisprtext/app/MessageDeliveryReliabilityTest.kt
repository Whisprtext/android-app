package com.whisprtext.app

import com.whisprtext.app.data.local.AppDatabase
import com.whisprtext.app.data.local.dao.ConversationDao
import com.whisprtext.app.data.local.dao.MessageDao
import com.whisprtext.app.data.local.dao.PendingReceiptDao
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.local.entity.PendingReceiptEntity
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.*
import java.io.IOException

/**
 * Tests for end-to-end delivery reliability scenarios:
 *  - Receipt queued to Room when HTTP fails (screen-off / offline)
 *  - syncReceipts() flushes pending receipts on reconnect
 *  - Read supersedes delivered in pending queue
 *  - No duplicate receipt entries for the same message
 *  - ReceiptUpdate WS events update local message status monotonically
 *  - WS reconnect triggers syncReceipts flush
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageDeliveryReliabilityTest {

    private val database: AppDatabase = mock()
    private val conversationDao: ConversationDao = mock()
    private val messageDao: MessageDao = mock()
    private val pendingReceiptDao: PendingReceiptDao = mock()
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
        whenever(database.pendingReceiptDao()).thenReturn(pendingReceiptDao)
        whenever(networkMonitor.isOnline).thenReturn(isOnlineFlow)
        whenever(webSocketManager.events).thenReturn(wsEventsFlow)
        whenever(preferencesManager.lastSyncTime).thenReturn(flowOf(null))
        whenever(preferencesManager.userId).thenReturn(flowOf("user-current"))

        kotlinx.coroutines.runBlocking {
            whenever(messageDao.getMessagesBySyncStatus(any())).thenReturn(emptyList())
            whenever(pendingReceiptDao.getAll()).thenReturn(emptyList())
        }

        repository = ChatRepository(
            database,
            apiClient,
            webSocketManager,
            networkMonitor,
            preferencesManager,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    // ─── sendReceiptReliably ─────────────────────────────────────────────────

    @Test
    fun sendReceiptReliably_enqueuesAndRemovesOnHttpSuccess() = runTest {
        whenever(apiClient.sendReceipt("msg-1", "delivered")).thenReturn(true)

        repository.sendReceiptReliably("msg-1", "conv-1", "delivered")

        verify(pendingReceiptDao).insert(argThat { messageId == "msg-1" && status == "delivered" })
        verify(pendingReceiptDao).deleteById(any())
    }

    @Test
    fun sendReceiptReliably_leavesEntryOnHttpFailure() = runTest {
        whenever(apiClient.sendReceipt(any(), any())).thenThrow(RuntimeException("offline"))

        repository.sendReceiptReliably("msg-2", "conv-1", "delivered")

        verify(pendingReceiptDao).insert(argThat { messageId == "msg-2" })
        verify(pendingReceiptDao, never()).deleteById(any())
    }

    @Test
    fun sendReceiptReliably_readClearsOlderDeliveredEntry() = runTest {
        whenever(apiClient.sendReceipt(any(), any())).thenReturn(true)

        repository.sendReceiptReliably("msg-3", "conv-1", "read")

        // "read" must delete any queued "delivered" entry before inserting itself.
        verify(pendingReceiptDao).deleteByMessageId("msg-3")
        verify(pendingReceiptDao).insert(argThat { messageId == "msg-3" && status == "read" })
    }

    // ─── syncReceipts ────────────────────────────────────────────────────────

    @Test
    fun syncReceipts_sendsAndDeletesOnSuccess() = runTest {
        val entry = PendingReceiptEntity(
            id = "pe-1", messageId = "msg-10", conversationId = "conv-1", status = "delivered"
        )
        whenever(pendingReceiptDao.getAll()).thenReturn(listOf(entry))
        whenever(apiClient.sendReceipt("msg-10", "delivered")).thenReturn(true)
        whenever(messageDao.getById("msg-10")).thenReturn(
            MessageEntity("msg-10", "conv-1", "user-other", "dev", "Hi", 1000L, "sent")
        )

        repository.syncReceipts()

        verify(apiClient).sendReceipt("msg-10", "delivered")
        verify(pendingReceiptDao).deleteById("pe-1")
    }

    @Test
    fun syncReceipts_incrementsAttemptsOnTransientFailure() = runTest {
        val entry = PendingReceiptEntity(
            id = "pe-2", messageId = "msg-11", conversationId = "conv-1", status = "delivered"
        )
        whenever(pendingReceiptDao.getAll()).thenReturn(listOf(entry))
        whenever(apiClient.sendReceipt(any(), any())).thenThrow(RuntimeException("timeout"))

        repository.syncReceipts()

        verify(pendingReceiptDao).incrementAttempts("pe-2")
        verify(pendingReceiptDao, never()).deleteById(any())
    }

    @Test
    fun syncReceipts_purgesExhaustedEntriesFirst() = runTest {
        whenever(pendingReceiptDao.getAll()).thenReturn(emptyList())

        repository.syncReceipts()

        verify(pendingReceiptDao).deleteExhausted(any())
    }

    // ─── WS event: new message triggers reliable receipt ────────────────────

    @Test
    fun incomingWsMessage_queuesDeliveredReceiptForOtherUser() = runTest {
        val incoming = MessageDto(
            id = "msg-ws-1", conversationId = "conv-ws", senderId = "user-other",
            senderDeviceId = "dev-other", encryptedContent = "Hey!", createdAt = "2026-07-15T10:00:00Z"
        )
        whenever(messageDao.getById(any())).thenReturn(null)
        whenever(conversationDao.getById(any())).thenReturn(null)
        whenever(apiClient.sendReceipt("msg-ws-1", "delivered")).thenReturn(true)

        wsEventsFlow.emit(WebSocketEvent.NewMessage(incoming))
        runCurrent()

        verify(pendingReceiptDao).insert(argThat { messageId == "msg-ws-1" && status == "delivered" })
    }

    @Test
    fun incomingWsMessage_noReceiptForOwnMessage() = runTest {
        val ownMsg = MessageDto(
            id = "msg-own", conversationId = "conv-ws", senderId = "user-current",
            senderDeviceId = "dev-self", encryptedContent = "My msg", createdAt = "2026-07-15T10:00:00Z"
        )
        whenever(messageDao.getById(any())).thenReturn(null)
        whenever(conversationDao.getById(any())).thenReturn(null)

        wsEventsFlow.emit(WebSocketEvent.NewMessage(ownMsg))
        runCurrent()

        verify(pendingReceiptDao, never()).insert(any())
    }

    // ─── ReceiptUpdate WS event ──────────────────────────────────────────────

    @Test
    fun receiptUpdateWs_upgradesLocalMessageStatus() = runTest {
        val existingMessage = MessageEntity(
            id = "msg-r1", conversationId = "conv-1", senderId = "user-current",
            senderDeviceId = "dev", encryptedContent = "Test", createdAt = 1000L, syncStatus = "sent"
        )
        whenever(messageDao.getById("msg-r1")).thenReturn(existingMessage)

        wsEventsFlow.emit(WebSocketEvent.ReceiptUpdate(
            ReceiptDto(messageId = "msg-r1", userId = "user-other", status = "delivered", updatedAt = "2026-07-15T10:00:00Z")
        ))
        runCurrent()

        verify(messageDao).insert(argThat { syncStatus == "delivered" })
    }

    @Test
    fun receiptUpdateWs_doesNotDowngradeLocalStatus() = runTest {
        val existingMessage = MessageEntity(
            id = "msg-r2", conversationId = "conv-1", senderId = "user-current",
            senderDeviceId = "dev", encryptedContent = "Test", createdAt = 1000L, syncStatus = "read"
        )
        whenever(messageDao.getById("msg-r2")).thenReturn(existingMessage)

        // Try to apply a lower-precedence "delivered" on an already-"read" message.
        wsEventsFlow.emit(WebSocketEvent.ReceiptUpdate(
            ReceiptDto(messageId = "msg-r2", userId = "user-other", status = "delivered", updatedAt = "2026-07-15T10:00:00Z")
        ))
        runCurrent()

        verify(messageDao, never()).insert(argThat { syncStatus == "delivered" })
    }

    // ─── WS reconnect flushes pending receipts ───────────────────────────────

    @Test
    fun wsConnected_triggersReceiptFlush() = runTest {
        val pendingEntry = PendingReceiptEntity(
            id = "pe-r", messageId = "msg-20", conversationId = "conv-1", status = "read"
        )
        whenever(pendingReceiptDao.getAll()).thenReturn(listOf(pendingEntry))
        whenever(apiClient.sendReceipt("msg-20", "read")).thenReturn(true)
        whenever(messageDao.getById("msg-20")).thenReturn(
            MessageEntity("msg-20", "conv-1", "user-other", "dev", "Hi", 1000L, "delivered")
        )

        wsEventsFlow.emit(WebSocketEvent.Connected)
        runCurrent()

        // After WS reconnect, pending receipts should be flushed.
        verify(apiClient).sendReceipt("msg-20", "read")
        verify(pendingReceiptDao).deleteById("pe-r")
    }
}
