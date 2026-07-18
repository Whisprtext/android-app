package com.whisprtext.app

import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.repository.ChatRepository
import com.whisprtext.app.data.remote.model.MeResponse
import com.whisprtext.app.data.remote.model.UserDto
import com.whisprtext.app.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val apiClient: ApiClient = mock()
    private val preferencesManager: PreferencesManager = mock()
    private val chatRepository: ChatRepository = mock()
    private lateinit var viewModel: ProfileViewModel

    private val initialUser = UserDto(
        id = "user-123",
        username = "alex",
        displayName = "Alex M",
        phoneNumber = "+15555551234",
        discoverableByUsername = true,
        discoverableByPhone = true,
        bio = "Hello",
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
        whenever(apiClient.getMe()).thenReturn(MeResponse(initialUser, mock()))
        viewModel = ProfileViewModel(apiClient, preferencesManager, chatRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testProfileFetchLoadsState() = runTest {
        runCurrent()
        val profile = viewModel.userProfile.value
        assertNotNull(profile)
        assertEquals("alex", profile?.username)
        assertEquals("Alex M", profile?.displayName)
        assertEquals("Hello", profile?.bio)
    }

    @Test
    fun testSaveProfileSuccess() = runTest {
        runCurrent()
        val updatedUser = initialUser.copy(
            username = "alex_new",
            displayName = "Alex New",
            bio = "New Bio",
            avatarUrl = "http://new"
        )
        whenever(apiClient.updateProfile("alex_new", "Alex New", "New Bio", "http://new"))
            .thenReturn(updatedUser)

        viewModel.saveProfile("alex_new", "Alex New", "New Bio", "http://new")
        runCurrent()

        assertEquals("alex_new", viewModel.userProfile.value?.username)
        assertEquals("Alex New", viewModel.userProfile.value?.displayName)
        assertEquals("New Bio", viewModel.userProfile.value?.bio)
        verify(preferencesManager).saveUsername("alex_new")
    }

    @Test
    fun testSaveProfileInvalidUsernameRejected() = runTest {
        runCurrent()
        viewModel.saveProfile("alex new name", "Alex", "", "")
        runCurrent()

        // State shouldn't change, error message should be set
        assertEquals("alex", viewModel.userProfile.value?.username)
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value!!.contains("username format", ignoreCase = true))
    }

    @Test
    fun testSavePrivacySettingsSuccess() = runTest {
        runCurrent()
        val updatedUser = initialUser.copy(
            phoneNumber = "+12223334444",
            discoverableByUsername = false,
            discoverableByPhone = false,
            phoneNumberVisibility = "contacts"
        )
        whenever(apiClient.updateSettings(
            eq("+12223334444"),
            eq(false),
            eq(false),
            eq("Alex M"),
            eq("contacts")
        )).thenReturn(updatedUser)

        viewModel.savePrivacySettings("+12223334444", false, false, "contacts")
        runCurrent()

        assertEquals("+12223334444", viewModel.userProfile.value?.phoneNumber)
        assertFalse(viewModel.userProfile.value!!.discoverableByUsername)
        assertFalse(viewModel.userProfile.value!!.discoverableByPhone)
        assertEquals("contacts", viewModel.userProfile.value?.phoneNumberVisibility)
    }

    @Test
    fun testSavePrivacySettingsInvalidPhoneRejected() = runTest {
        runCurrent()
        viewModel.savePrivacySettings("12345", false, false, "everyone")
        runCurrent()

        assertEquals("+15555551234", viewModel.userProfile.value?.phoneNumber)
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value!!.contains("phone number format", ignoreCase = true))
    }
}
