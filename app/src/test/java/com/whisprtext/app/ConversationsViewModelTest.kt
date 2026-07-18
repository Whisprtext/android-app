package com.whisprtext.app

import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.repository.ChatRepository
import com.whisprtext.app.data.repository.ContactRepository
import com.whisprtext.app.ui.viewmodel.ConversationsViewModel
import com.whisprtext.app.data.remote.model.MeResponse
import com.whisprtext.app.data.remote.model.UserDto
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val chatRepository: ChatRepository = mock()
    private val contactRepository: ContactRepository = mock()
    private val preferencesManager: PreferencesManager = mock()
    private lateinit var viewModel: ConversationsViewModel

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(testDispatcher)
        val dummyList = listOf(
            ConversationEntity("conv-1", "direct", 1000L, 0, "Last Msg", 1000L)
        )
        whenever(chatRepository.getConversations()).thenReturn(flowOf(dummyList))
        whenever(contactRepository.contactsMap).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(emptyMap()))
        whenever(preferencesManager.username).thenReturn(flowOf("testuser"))
        whenever(preferencesManager.displayName).thenReturn(flowOf(null))
        whenever(preferencesManager.avatarUrl).thenReturn(flowOf(null))
        whenever(preferencesManager.gradientStart).thenReturn(flowOf(null))
        whenever(preferencesManager.gradientEnd).thenReturn(flowOf(null))
        whenever(chatRepository.getCachedSelfProfile()).thenReturn(UserDto("user-123", "testuser"))
        whenever(chatRepository.getConversations()).thenReturn(flowOf(dummyList))
        viewModel = ConversationsViewModel(chatRepository, contactRepository, preferencesManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialSyncAndListUpdate() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect {}
        }
        runCurrent()
        verify(chatRepository).syncConversations()

        val state = viewModel.uiState.value
        assertEquals(1, state.conversations.size)
        assertEquals("conv-1", state.conversations[0].id)
        assertEquals(false, state.isLoading)
        collectJob.cancel()
    }

    @Test
    fun testCreateConversationTriggersRepository() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.uiState.collect {}
        }
        viewModel.createConversation(listOf("user-abc"))
        runCurrent()
        verify(chatRepository).createDirectConversation("user-abc", null)
        collectJob.cancel()
    }
}
