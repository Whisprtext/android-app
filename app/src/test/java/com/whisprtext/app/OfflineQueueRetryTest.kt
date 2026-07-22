package com.whisprtext.app

import com.whisprtext.app.data.local.AppDatabase
import com.whisprtext.app.data.local.dao.ConversationDao
import com.whisprtext.app.data.local.dao.MessageDao
import com.whisprtext.app.data.local.dao.PendingReceiptDao
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.remote.WebSocketManager
import com.whisprtext.app.data.remote.model.MessageDto
import com.whisprtext.app.data.repository.ChatRepository
import com.whisprtext.app.util.NetworkMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineQueueRetryTest {

    private val database: AppDatabase = mock()
    private val conversationDao: ConversationDao = mock()
    private val messageDao: MessageDao = mock()
    private val pendingReceiptDao: PendingReceiptDao = mock()
    private val apiClient: ApiClient = mock()
    private val webSocketManager: WebSocketManager = mock()
    private val networkMonitor: NetworkMonitor = mock()
    private val preferencesManager: com.whisprtext.app.data.local.PreferencesManager = mock()
    
    private val isOnlineFlow = MutableStateFlow(true)
    private val wsEventsFlow = MutableSharedFlow<com.whisprtext.app.data.remote.WebSocketEvent>()
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
        whenever(preferencesManager.lastSyncTime).thenReturn(kotlinx.coroutines.flow.flowOf(null))
        whenever(preferencesManager.userId).thenReturn(kotlinx.coroutines.flow.flowOf("user-current"))
        whenever(preferencesManager.isTranslationEnabled).thenReturn(kotlinx.coroutines.flow.flowOf(false))
        whenever(preferencesManager.preferredTargetLanguage).thenReturn(kotlinx.coroutines.flow.flowOf("eng_Latn"))
        
        // Mock default database response for failed messages query
        run {
            kotlinx.coroutines.runBlocking {
                whenever(preferencesManager.getDeviceId()).thenReturn("dev-1")
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
            ioDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(),
        )
    }

    @Test
    fun testSendMessageOfflineQueuesFailedMessage() = runTest {
        whenever(apiClient.sendMessage(any(), any())).thenAnswer { throw IOException("No network") }
        whenever(conversationDao.getById("conv-123")).thenReturn(
            ConversationEntity("conv-123", "direct", 1000L, 0, null, null)
        )

        repository.sendMessage("conv-123", "Hello Offline", "user-1", "dev-1")

        // Capture inserts to verify optimistic insert and subsequent status change to "failed"
        val msgCaptor = argumentCaptor<MessageEntity>()
        verify(messageDao, times(2)).insert(msgCaptor.capture())

        val optMsg = msgCaptor.firstValue
        assertEquals("pending", optMsg.syncStatus)
        assertEquals("Hello Offline", optMsg.encryptedContent)

        val failedMsg = msgCaptor.secondValue
        assertEquals("failed", failedMsg.syncStatus)
        assertEquals("Hello Offline", failedMsg.encryptedContent)

        // Verify conversation last message is updated optimistically
        val convCaptor = argumentCaptor<ConversationEntity>()
        verify(conversationDao).insert(convCaptor.capture())
        assertEquals("Hello Offline", convCaptor.firstValue.lastMessageText)
    }

    @Test
    fun testRetryFailedMessagesRequiresE2EE_noPlaintextWireSend() = runTest {
        val failedMsg = MessageEntity(
            id = "temp-failed-id",
            conversationId = "conv-123",
            senderId = "user-1",
            senderDeviceId = "dev-1",
            encryptedContent = "Hello Offline",
            createdAt = 1000L,
            syncStatus = "failed",
            decryptionStatus = "decrypted"
        )
        whenever(messageDao.getMessagesBySyncStatus("failed")).thenReturn(listOf(failedMsg))
        whenever(conversationDao.getById("conv-123")).thenReturn(
            ConversationEntity("conv-123", "direct", 1000L, 0, "Hello Offline", 1000L, username = "bob")
        )
        whenever(preferencesManager.getDeviceId()).thenReturn("dev-1")

        // Without SignalKeyManager in this unit test, retry must not fall back to plaintext
        repository.retryFailedMessages()

        val msgCaptor = argumentCaptor<MessageEntity>()
        verify(messageDao, atLeastOnce()).insert(msgCaptor.capture())
        assert(msgCaptor.allValues.any { it.syncStatus == "failed" })
        verify(apiClient, never()).sendMessage(any(), any())
    }
}
