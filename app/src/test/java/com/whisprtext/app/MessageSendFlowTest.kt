package com.whisprtext.app

import com.whisprtext.app.data.local.AppDatabase
import com.whisprtext.app.data.local.dao.ConversationDao
import com.whisprtext.app.data.local.dao.MessageDao
import com.whisprtext.app.data.local.dao.PendingReceiptDao
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MessageSendFlowTest {

    private val database: AppDatabase = mock()
    private val conversationDao: ConversationDao = mock()
    private val messageDao: MessageDao = mock()
    private val pendingReceiptDao: PendingReceiptDao = mock()
    private val apiClient: ApiClient = mock()
    private val webSocketManager: com.whisprtext.app.data.remote.WebSocketManager = mock()
    private val networkMonitor: com.whisprtext.app.util.NetworkMonitor = mock()
    private val preferencesManager: com.whisprtext.app.data.local.PreferencesManager = mock()
    private lateinit var repository: ChatRepository

    @Before
    fun setUp() {
        whenever(database.conversationDao()).thenReturn(conversationDao)
        whenever(database.messageDao()).thenReturn(messageDao)
        whenever(database.pendingReceiptDao()).thenReturn(pendingReceiptDao)
        whenever(networkMonitor.isOnline).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(true))
        whenever(webSocketManager.events).thenReturn(kotlinx.coroutines.flow.MutableSharedFlow())
        whenever(preferencesManager.userId).thenReturn(kotlinx.coroutines.flow.flowOf("user-123"))
        whenever(preferencesManager.lastSyncTime).thenReturn(kotlinx.coroutines.flow.flowOf(null))
        kotlinx.coroutines.runBlocking {
            whenever(pendingReceiptDao.getAll()).thenReturn(emptyList())
            whenever(messageDao.getMessagesBySyncStatus(any())).thenReturn(emptyList())
            whenever(preferencesManager.getDeviceId()).thenReturn("dev-1")
            whenever(conversationDao.getById(any())).thenReturn(
                ConversationEntity("conv-1", "direct", 1000L, 0, null, null, username = "bob")
            )
        }
        // No appContext → SignalKeyManager unavailable; E2EE required so send must not fall back to plaintext
        repository = ChatRepository(database, apiClient, webSocketManager, networkMonitor, preferencesManager)
    }

    @Test
    fun testSendMessageFlowOptimisticAndFinalPersistence() = runTest {
        repository.sendMessage("conv-1", "Hey there!", "user-123", "dev-1")

        val optCaptor = argumentCaptor<MessageEntity>()
        verify(messageDao, atLeastOnce()).insert(optCaptor.capture())

        val optMessage = optCaptor.allValues.first { it.syncStatus == "pending" }
        assert(optMessage.encryptedContent == "Hey there!") // LocalEncryptor disabled without appContext
        assert(optMessage.decryptionStatus == "decrypted")

        // Without SignalKeyManager, plaintext fallback is forbidden → ends failed
        val failed = optCaptor.allValues.any { it.syncStatus == "failed" }
        assert(failed)

        // Must never POST plaintext content over the wire
        verify(apiClient, never()).sendMessage(any(), any())
    }
}
