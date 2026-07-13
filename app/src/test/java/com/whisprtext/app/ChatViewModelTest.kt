package com.whisprtext.app

import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.repository.ChatRepository
import com.whisprtext.app.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val chatRepository: ChatRepository = mock()
    private val preferencesManager: PreferencesManager = mock()
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(preferencesManager.userId).thenReturn(flowOf("user-123"))
        val dummyMessages = listOf(
            MessageEntity("msg-1", "conv-1", "user-123", "dev-1", "Hello", 1000L, "sent")
        )
        whenever(chatRepository.getMessages("conv-1")).thenReturn(flowOf(dummyMessages))
        whenever(chatRepository.getConversationFlow("conv-1")).thenReturn(flowOf(null))
        viewModel = ChatViewModel("conv-1", chatRepository, preferencesManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSendMessageTriggersRepository() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect {}
        }
        runCurrent()
        viewModel.sendMessage("Hey there")
        runCurrent()
        verify(chatRepository).sendMessage(eq("conv-1"), eq("Hey there"), eq("user-123"), any())
        collectJob.cancel()
    }
}
