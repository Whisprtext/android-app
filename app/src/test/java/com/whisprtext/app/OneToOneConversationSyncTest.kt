package com.whisprtext.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.whisprtext.app.data.local.AppDatabase
import com.whisprtext.app.data.local.dao.ConversationDao
import com.whisprtext.app.data.local.dao.MessageDao
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.remote.model.ConversationSummaryDto
import com.whisprtext.app.data.remote.model.MessageDto
import com.whisprtext.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class OneToOneConversationSyncTest {

    private lateinit var db: AppDatabase
    private lateinit var conversationDao: ConversationDao
    private lateinit var messageDao: MessageDao
    private val apiClient: ApiClient = mock()
    private val webSocketManager: com.whisprtext.app.data.remote.WebSocketManager = mock()
    private val networkMonitor: com.whisprtext.app.util.NetworkMonitor = mock()
    private val preferencesManager: com.whisprtext.app.data.local.PreferencesManager = mock()
    private lateinit var repository: ChatRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        conversationDao = db.conversationDao()
        messageDao = db.messageDao()
        whenever(networkMonitor.isOnline).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(true))
        whenever(webSocketManager.events).thenReturn(kotlinx.coroutines.flow.MutableSharedFlow())
        repository = ChatRepository(db, apiClient, webSocketManager, networkMonitor, preferencesManager)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testRoomClearAndSyncRestoresConversationAndDisplayName() = runBlocking {
        // 1. Populate initial data locally
        val localConv = ConversationEntity(
            id = "conv-direct-123",
            type = "direct",
            createdAt = System.currentTimeMillis(),
            unreadCount = 0,
            lastMessageText = "Initial message",
            lastMessageTime = System.currentTimeMillis(),
            title = "Maria",
            username = "maria_12"
        )
        conversationDao.insert(localConv)

        var conversations = conversationDao.getConversationsFlow().first()
        assertEquals(1, conversations.size)
        assertEquals("Maria", conversations[0].title)

        // 2. Clear local Room database tables to simulate cache purge
        db.clearAllTables()

        conversations = conversationDao.getConversationsFlow().first()
        assertEquals(0, conversations.size) // Cache is completely empty

        // 3. Mock the ApiClient to return the exact same conversation on sync
        val remoteSummary = ConversationSummaryDto(
            id = "conv-direct-123",
            type = "direct",
            createdAt = "2026-07-13T12:00:00Z",
            unreadCount = 0,
            lastMessage = MessageDto(
                id = "msg-999",
                conversationId = "conv-direct-123",
                senderId = "maria-uuid",
                senderDeviceId = "dev-maria",
                encryptedContent = "Hello from backend sync",
                createdAt = "2026-07-13T12:00:00Z"
            ),
            displayName = "Maria",
            username = "maria_12"
        )
        whenever(apiClient.getConversations(anyOrNull(), anyOrNull())).thenReturn(listOf(remoteSummary))

        // 4. Trigger backend sync delta
        repository.syncConversations()

        // 5. Verify conversation and participant display names are restored from backend delta payload
        conversations = conversationDao.getConversationsFlow().first()
        assertEquals(1, conversations.size)
        assertEquals("conv-direct-123", conversations[0].id)
        assertEquals("Maria", conversations[0].title)
        assertEquals("maria_12", conversations[0].username)
        assertEquals("Hello from backend sync", conversations[0].lastMessageText)
    }
}
