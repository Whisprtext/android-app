package com.whisprtext.app

import com.whisprtext.app.data.local.AppDatabase
import com.whisprtext.app.data.local.dao.ConversationDao
import com.whisprtext.app.data.local.dao.MessageDao
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.remote.model.ConversationSummaryDto
import com.whisprtext.app.data.remote.model.MessageDto
import com.whisprtext.app.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryTest {

    private val database: AppDatabase = mock()
    private val conversationDao: ConversationDao = mock()
    private val messageDao: MessageDao = mock()
    private val apiClient: ApiClient = mock()
    private val webSocketManager: com.whisprtext.app.data.remote.WebSocketManager = mock()
    private val networkMonitor: com.whisprtext.app.util.NetworkMonitor = mock()
    private val preferencesManager: com.whisprtext.app.data.local.PreferencesManager = mock()
    private lateinit var repository: ChatRepository

    @Before
    fun setUp() {
        whenever(database.conversationDao()).thenReturn(conversationDao)
        whenever(database.messageDao()).thenReturn(messageDao)
        whenever(networkMonitor.isOnline).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(true))
        whenever(webSocketManager.events).thenReturn(kotlinx.coroutines.flow.MutableSharedFlow())
        repository = ChatRepository(database, apiClient, webSocketManager, networkMonitor, preferencesManager)
    }

    @Test
    fun testSyncConversationsSavesToLocalDb() = runTest {
        val dummySummary = ConversationSummaryDto(
            id = "conv-123",
            type = "direct",
            createdAt = "2026-07-12T12:00:00Z",
            unreadCount = 2,
            lastMessage = MessageDto(
                id = "msg-123",
                conversationId = "conv-123",
                senderId = "sender-123",
                senderDeviceId = "device-123",
                encryptedContent = "Encrypted Hello",
                createdAt = "2026-07-12T12:00:00Z"
            )
        )
        whenever(apiClient.getConversations(anyOrNull(), anyOrNull())).thenReturn(listOf(dummySummary))

        repository.syncConversations()

        val convCaptor = argumentCaptor<List<ConversationEntity>>()
        verify(conversationDao).insertAll(convCaptor.capture())
        
        val insertedConvs = convCaptor.firstValue
        assert(insertedConvs.size == 1)
        assert(insertedConvs[0].id == "conv-123")
        assert(insertedConvs[0].unreadCount == 2)
    }
}
