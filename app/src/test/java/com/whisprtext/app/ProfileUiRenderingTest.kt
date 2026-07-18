package com.whisprtext.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.repository.ChatRepository
import com.whisprtext.app.data.remote.model.MeResponse
import com.whisprtext.app.data.remote.model.UserDto
import com.whisprtext.app.ui.screen.ProfileScreen
import com.whisprtext.app.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileUiRenderingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()
    private val apiClient: ApiClient = mock()
    private val preferencesManager: PreferencesManager = mock()
    private val chatRepository: ChatRepository = mock()
    private lateinit var viewModel: ProfileViewModel

    private val testUser = UserDto(
        id = "user-123",
        username = "alexmercer",
        displayName = "Alex Mercer",
        phoneNumber = "+15555551234",
        discoverableByUsername = true,
        discoverableByPhone = true,
        bio = "Bio Status Text Here",
        avatarUrl = "http://avatar",
        phoneNumberVisibility = "everyone"
    )

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(testDispatcher)
        whenever(preferencesManager.userId).thenReturn(kotlinx.coroutines.flow.flowOf("user-123"))
        whenever(preferencesManager.lastSyncTime).thenReturn(kotlinx.coroutines.flow.flowOf(null))
        whenever(preferencesManager.gradientStart).thenReturn(kotlinx.coroutines.flow.flowOf(null))
        whenever(preferencesManager.gradientEnd).thenReturn(kotlinx.coroutines.flow.flowOf(null))
        whenever(chatRepository.getCachedSelfProfile()).thenReturn(testUser)
        viewModel = ProfileViewModel(apiClient, preferencesManager, chatRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun profileScreenRendersUserProfileAndPrivacyOptions() {
        // Trigger flow execution to load testUser
        testDispatcher.scheduler.runCurrent()

        composeTestRule.setContent {
            ProfileScreen(
                viewModel = viewModel,
                onBackClick = {}
            )
        }

        // Check text display and outline fields
        composeTestRule.onNodeWithText("Profile Info").assertExists()
        composeTestRule.onNodeWithText("Privacy & Discovery").assertExists()
        composeTestRule.onNodeWithText("Alex Mercer").assertExists()
        composeTestRule.onNodeWithText("alexmercer").assertExists()
        composeTestRule.onNodeWithText("Bio Status Text Here").assertExists()
        composeTestRule.onNodeWithText("Who can see my phone number").assertExists()
        // URL field removed; avatar is managed via photo picker/editor.
        composeTestRule.onNodeWithText("Tap to change profile photo").assertExists()
    }
}
