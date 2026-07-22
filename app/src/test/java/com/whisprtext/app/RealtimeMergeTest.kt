package com.whisprtext.app

import com.whisprtext.app.data.local.AppDatabase
import com.whisprtext.app.data.local.dao.ConversationDao
import com.whisprtext.app.data.local.dao.MessageDao
import com.whisprtext.app.data.local.dao.PendingReceiptDao
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.remote.WebSocketEvent
import com.whisprtext.app.data.remote.WebSocketManager
import com.whisprtext.app.data.remote.model.DeltaSyncDto
import com.whisprtext.app.data.remote.model.MessageDto
import com.whisprtext.app.data.repository.ChatRepository
import com.whisprtext.app.util.NetworkMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeMergeTest {

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

    private val outboxDao: com.whisprtext.app.data.local.dao.OutboxDao = mock()
    private val userProfileDao: com.whisprtext.app.data.local.dao.UserProfileDao = mock()

    @Before
    fun setUp() {
        whenever(database.conversationDao()).thenReturn(conversationDao)
        whenever(database.messageDao()).thenReturn(messageDao)
        whenever(database.pendingReceiptDao()).thenReturn(pendingReceiptDao)
        whenever(database.outboxDao()).thenReturn(outboxDao)
        whenever(database.userProfileDao()).thenReturn(userProfileDao)
        whenever(networkMonitor.isOnline).thenReturn(isOnlineFlow)
        whenever(webSocketManager.events).thenReturn(wsEventsFlow)
        whenever(preferencesManager.lastSyncTime).thenReturn(flowOf(null))
        whenever(preferencesManager.userId).thenReturn(flowOf("user-current"))
        whenever(preferencesManager.isTranslationEnabled).thenReturn(flowOf(false))
        whenever(preferencesManager.preferredTargetLanguage).thenReturn(flowOf("eng_Latn"))
        
        run {
            kotlinx.coroutines.runBlocking {
                whenever(messageDao.getMessagesBySyncStatus(any())).thenReturn(emptyList())
                whenever(messageDao.getUnreadReceivedMessages(any())).thenReturn(emptyList())
                whenever(pendingReceiptDao.getAll()).thenReturn(emptyList())
            }
        }

        repository = ChatRepository(
            database,
            apiClient,
            webSocketManager,
            networkMonitor,
            preferencesManager,
            appContext = null,
            ioDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
        )
    }

    @Test
    fun testWebSocketMessageEventPersistsToDatabase() = runTest {
        val incomingMsg = MessageDto(
            id = "msg-999",
            conversationId = "conv-123",
            senderId = "user-1",
            senderDeviceId = "dev-1",
            encryptedContent = "Realtime Msg",
            createdAt = "2026-07-12T12:00:00Z"
        )
        whenever(conversationDao.getById("conv-123")).thenReturn(
            ConversationEntity("conv-123", "direct", 1000L, 0, null, null)
        )

        // Emit NewMessage event on WebSocket events flow
        wsEventsFlow.emit(WebSocketEvent.NewMessage(incomingMsg))
        runCurrent()

        // Verify it parsed and saved to local DB
        val msgCaptor = argumentCaptor<MessageEntity>()
        verify(messageDao).insert(msgCaptor.capture())

        val savedEntity = msgCaptor.firstValue
        assert(savedEntity.id == "msg-999")
        assert(savedEntity.syncStatus == "delivered")
        assert(savedEntity.encryptedContent == "Realtime Msg")
    }

    @Test
    fun testSyncDeltaMergesMessagesAndUpdatesSyncTime() = runTest {
        val dummyDelta = DeltaSyncDto(
            messages = listOf(
                MessageDto(
                    id = "msg-abc",
                    conversationId = "conv-123",
                    senderId = "user-2",
                    senderDeviceId = "dev-2",
                    encryptedContent = "Hey",
                    createdAt = "2026-07-12T12:00:00Z"
                )
            ),
            receipts = emptyList(),
            currentTime = "2026-07-12T12:05:00Z"
        )
        whenever(preferencesManager.lastSyncTime).thenReturn(flowOf("2026-07-12T11:00:00Z"))
        whenever(apiClient.sync("2026-07-12T11:00:00Z")).thenReturn(dummyDelta)
        whenever(conversationDao.getById("conv-123")).thenReturn(
            ConversationEntity("conv-123", "direct", 1000L, 0, null, null)
        )

        repository.syncDelta()

        // Verify messages insert is invoked
        val msgCaptor = argumentCaptor<MessageEntity>()
        verify(messageDao).insert(msgCaptor.capture())
        assert(msgCaptor.firstValue.id == "msg-abc")

        // Verify lastSyncTime key is updated
        verify(preferencesManager).saveLastSyncTime("2026-07-12T12:05:00Z")
    }
}
