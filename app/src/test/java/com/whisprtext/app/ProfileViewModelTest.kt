package com.whisprtext.app

import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.repository.ChatRepository
import com.whisprtext.app.data.remote.model.AvatarUploadInitResponse
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
import org.mockito.Mockito.never
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
        avatarUrl = "r2://avatars/user-123/old.jpg",
        phoneNumberVisibility = "everyone"
    )

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(testDispatcher)
        whenever(preferencesManager.userId).thenReturn(kotlinx.coroutines.flow.flowOf("user-123"))
        whenever(preferencesManager.lastSyncTime).thenReturn(kotlinx.coroutines.flow.flowOf(null))
        whenever(preferencesManager.gradientStart).thenReturn(kotlinx.coroutines.flow.flowOf(null))
        whenever(preferencesManager.gradientEnd).thenReturn(kotlinx.coroutines.flow.flowOf(null))
        // Local-first: own profile loads from cache, no network.
        whenever(chatRepository.getCachedSelfProfile()).thenReturn(initialUser)
        viewModel = ProfileViewModel(apiClient, preferencesManager, chatRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testProfileLoadsFromLocalCacheWithoutNetwork() = runTest {
        runCurrent()
        val profile = viewModel.userProfile.value
        assertNotNull(profile)
        assertEquals("alex", profile?.username)
        assertEquals("Alex M", profile?.displayName)
        assertEquals("Hello", profile?.bio)
        assertEquals("r2://avatars/user-123/old.jpg", profile?.avatarUrl)
        verify(chatRepository, never()).refreshOwnProfileFromNetwork()
    }

    @Test
    fun testSaveProfileSuccessUpdatesLocalCacheViaRepository() = runTest {
        runCurrent()
        val updatedUser = initialUser.copy(
            username = "alex_new",
            displayName = "Alex New",
            bio = "New Bio"
        )
        whenever(chatRepository.updateProfile("alex_new", "Alex New", "New Bio", ""))
            .thenReturn(updatedUser)

        viewModel.saveProfile("alex_new", "Alex New", "New Bio")
        runCurrent()

        assertEquals("alex_new", viewModel.userProfile.value?.username)
        assertEquals("Alex New", viewModel.userProfile.value?.displayName)
        assertEquals("New Bio", viewModel.userProfile.value?.bio)
        assertEquals("r2://avatars/user-123/old.jpg", viewModel.userProfile.value?.avatarUrl)
        verify(chatRepository).updateProfile("alex_new", "Alex New", "New Bio", "")
    }

    @Test
    fun testSaveProfileInvalidUsernameRejected() = runTest {
        runCurrent()
        viewModel.saveProfile("alex new name", "Alex", "")
        runCurrent()

        assertEquals("alex", viewModel.userProfile.value?.username)
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value!!.contains("username format", ignoreCase = true))
    }

    @Test
    fun testUploadAvatarSuccessUpdatesCurrentUserState() = runTest {
        runCurrent()
        val bytes = ByteArray(128) { 1 }
        val newAvatar = "r2://avatars/user-123/new.jpg"
        whenever(apiClient.initAvatarUpload("image/jpeg", 128L)).thenReturn(
            AvatarUploadInitResponse(
                uploadUrl = "https://storage/upload",
                fileUrl = newAvatar,
                fileId = "new"
            )
        )
        whenever(apiClient.uploadAvatarFile(eq("https://storage/upload"), any(), eq("image/jpeg"))).thenReturn(true)
        whenever(chatRepository.setAvatar(eq("new"), eq(newAvatar), eq("image/jpeg"), eq(128L))).thenReturn(
            initialUser.copy(avatarUrl = newAvatar)
        )

        var completed = false
        viewModel.uploadAvatar(bytes) { success -> completed = success }
        runCurrent()

        assertTrue(completed)
        assertEquals(newAvatar, viewModel.userProfile.value?.avatarUrl)
        assertFalse(viewModel.isAvatarUploading.value)
    }

    @Test
    fun testUploadAvatarFailurePreservesPreviousAvatar() = runTest {
        runCurrent()
        val previous = viewModel.userProfile.value?.avatarUrl
        val bytes = ByteArray(64) { 2 }
        whenever(apiClient.initAvatarUpload(any(), any())).thenThrow(RuntimeException("network down"))

        var completed = true
        viewModel.uploadAvatar(bytes) { success -> completed = success }
        runCurrent()

        assertFalse(completed)
        assertEquals(previous, viewModel.userProfile.value?.avatarUrl)
        assertNotNull(viewModel.errorMessage.value)
        verify(chatRepository, never()).setAvatar(any(), any(), any(), any())
    }

    @Test
    fun testUploadAvatarRejectsOversized() = runTest {
        runCurrent()
        val huge = ByteArray(3 * 1024 * 1024)
        var completed = true
        viewModel.uploadAvatar(huge) { success -> completed = success }
        runCurrent()
        assertFalse(completed)
        assertEquals("r2://avatars/user-123/old.jpg", viewModel.userProfile.value?.avatarUrl)
        verify(apiClient, never()).initAvatarUpload(any(), any())
    }

    @Test
    fun testRemoveAvatarSuccessClearsAvatar() = runTest {
        runCurrent()
        whenever(chatRepository.removeAvatar()).thenReturn(initialUser.copy(avatarUrl = ""))

        viewModel.removeAvatar()
        runCurrent()

        assertEquals("", viewModel.userProfile.value?.avatarUrl)
    }

    @Test
    fun testRemoveAvatarFailurePreservesPreviousAvatar() = runTest {
        runCurrent()
        val previous = viewModel.userProfile.value?.avatarUrl
        whenever(chatRepository.removeAvatar()).thenThrow(RuntimeException("offline"))

        viewModel.removeAvatar()
        runCurrent()

        assertEquals(previous, viewModel.userProfile.value?.avatarUrl)
        assertNotNull(viewModel.errorMessage.value)
    }

    @Test
    fun testOtherProfileLoadsCacheThenRefreshes() = runTest {
        val other = initialUser.copy(id = "u-2", username = "maria", displayName = "Maria")
        whenever(chatRepository.getCachedProfileByUsername("maria")).thenReturn(other)
        whenever(chatRepository.refreshContactProfile("maria")).thenReturn(
            other.copy(avatarUrl = "r2://avatars/u-2/new.jpg", bio = "Hi")
        )
        whenever(chatRepository.getDirectConversationByContact("maria", null)).thenReturn(null)

        val otherVm = ProfileViewModel(apiClient, preferencesManager, chatRepository, targetUsername = "maria")
        runCurrent()

        assertEquals("maria", otherVm.userProfile.value?.username)
        assertEquals("r2://avatars/u-2/new.jpg", otherVm.userProfile.value?.avatarUrl)
        assertEquals("Hi", otherVm.userProfile.value?.bio)
        verify(chatRepository).refreshContactProfile("maria")
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
        whenever(chatRepository.updateSettings(
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
