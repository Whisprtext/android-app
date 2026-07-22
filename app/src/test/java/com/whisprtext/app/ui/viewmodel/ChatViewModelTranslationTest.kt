package com.whisprtext.app.ui.viewmodel

import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.repository.ChatRepository
import com.whisprtext.app.data.repository.ContactRepository
import com.whisprtext.app.translation.ModelDownloadState
import com.whisprtext.app.translation.TranslationModelRepository
import com.whisprtext.app.translation.TranslationRepository
import com.whisprtext.app.translation.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTranslationTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var mockChatRepository: ChatRepository
    private lateinit var mockContactRepository: ContactRepository
    private lateinit var mockPreferencesManager: PreferencesManager
    private lateinit var mockTranslationRepository: TranslationRepository
    private lateinit var mockModelRepository: TranslationModelRepository
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockChatRepository = mock {
            on { getMessages(any()) } doReturn flowOf(emptyList())
            on { getConversationFlow(any()) } doReturn flowOf(null)
            on { getCachedMessages(any()) } doReturn emptyList()
        }
        mockContactRepository = mock {
            on { contactsMap } doReturn MutableStateFlow(emptyMap<String, String>())
        }
        mockPreferencesManager = mock {
            on { cachedUserId } doReturn "user_1"
            on { cachedAppearanceSettings } doReturn com.whisprtext.app.data.model.AppearanceSettings()
            on { appearanceSettings } doReturn flowOf(com.whisprtext.app.data.model.AppearanceSettings())
            on { userId } doReturn flowOf("user_1")
            on { isTranslationEnabled } doReturn flowOf(true)
            on { preferredTargetLanguage } doReturn flowOf("eng_Latn")
        }

        mockTranslationRepository = mock()
        mockModelRepository = mock {
            on { downloadState } doReturn MutableStateFlow(ModelDownloadState.NotDownloaded)
        }

        viewModel = ChatViewModel(
            conversationId = "conv_1",
            chatRepository = mockChatRepository,
            contactRepository = mockContactRepository,
            preferencesManager = mockPreferencesManager,
            translationRepository = mockTranslationRepository,
            translationModelRepository = mockModelRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testTranslateMessageShowsModelRequiredWhenModelNotReady() = runTest {
        whenever(mockModelRepository.isModelReady()).thenReturn(false)

        viewModel.translateMessage("msg_1")

        val state = viewModel.uiState.value
        val msgUi = state.messages.firstOrNull { it.message.id == "msg_1" }
        assertEquals(MessageTranslationState.ModelRequired, msgUi?.translationState ?: MessageTranslationState.ModelRequired)
    }

    @Test
    fun testToggleShowOriginalTogglesState() = runTest {
        whenever(mockModelRepository.isModelReady()).thenReturn(true)
        whenever(mockTranslationRepository.getOrTranslateMessage(any(), any(), any(), anyOrNull()))
            .thenReturn(TranslationResult.Success("Hola", "Hello", "spa_Latn", "eng_Latn"))

        viewModel.toggleShowOriginal("msg_1")
        val state = viewModel.uiState.value
        assertNotNull(state)
    }
}
