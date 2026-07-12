package com.whisprtext.app

import com.whisprtext.app.data.local.AppDatabase
import com.whisprtext.app.data.local.dao.ConversationDao
import com.whisprtext.app.data.local.dao.MessageDao
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.remote.model.MessageDto
import com.whisprtext.app.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.times
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
    private val apiClient: ApiClient = mock()
    private lateinit var repository: ChatRepository

    @Before
    fun setUp() {
        whenever(database.conversationDao()).thenReturn(conversationDao)
        whenever(database.messageDao()).thenReturn(messageDao)
        repository = ChatRepository(database, apiClient)
    }

    @Test
    fun testSendMessageFlowOptimisticAndFinalPersistence() = runTest {
        val dummyResponseDto = MessageDto(
            id = "final-msg-123",
            conversationId = "conv-1",
            senderId = "user-123",
            senderDeviceId = "dev-1",
            encryptedContent = "Hey there!",
            createdAt = "2026-07-12T12:00:00Z"
        )
        whenever(apiClient.sendMessage("conv-1", "Hey there!")).thenReturn(dummyResponseDto)

        repository.sendMessage("conv-1", "Hey there!", "user-123", "dev-1")

        val optCaptor = argumentCaptor<MessageEntity>()
        verify(messageDao, times(2)).insert(optCaptor.capture())

        val optMessage = optCaptor.firstValue
        assert(optMessage.encryptedContent == "Hey there!")
        assert(optMessage.syncStatus == "sending")

        verify(messageDao).deleteById(optMessage.id)

        val finalMessage = optCaptor.secondValue
        assert(finalMessage.id == "final-msg-123")
        assert(finalMessage.syncStatus == "sent")
    }
}
