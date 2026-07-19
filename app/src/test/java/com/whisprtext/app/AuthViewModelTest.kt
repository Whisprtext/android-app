package com.whisprtext.app

import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.remote.ApiClient
import com.whisprtext.app.data.remote.model.AuthResponse
import com.whisprtext.app.data.remote.model.DeviceDto
import com.whisprtext.app.data.remote.model.UserDto
import com.whisprtext.app.ui.viewmodel.AuthState
import com.whisprtext.app.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val apiClient: ApiClient = mock()
    private val preferencesManager: PreferencesManager = mock()
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        runBlocking {
            whenever(preferencesManager.getOrCreateDeviceName()).thenReturn("Android Phone")
        }
        viewModel = AuthViewModel(apiClient, preferencesManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLoginSuccessTransitions() = runTest {
        val dummyResponse = AuthResponse(
            user = UserDto("user-123", "alice"),
            device = DeviceDto("device-123", "user-123", "Android Phone"),
            sessionToken = "dummy-token"
        )
        whenever(apiClient.login("alice", "password", "Android Phone")).thenReturn(dummyResponse)

        viewModel.login("alice", "password")

        runCurrent()

        assertEquals(AuthState.Success, viewModel.authState.value)
        verify(preferencesManager).saveSession(
            org.mockito.kotlin.eq("dummy-token"),
            org.mockito.kotlin.eq("user-123"),
            org.mockito.kotlin.eq("alice"),
            any(),
            any(),
            org.mockito.kotlin.eq("device-123")
        )
    }

    @Test
    fun testLoginFailureTransitions() = runTest {
        whenever(apiClient.login(any(), any(), any())).thenThrow(RuntimeException("Connection failed"))

        viewModel.login("alice", "password")

        runCurrent()

        assertTrue(viewModel.authState.value is AuthState.Error)
        assertEquals("Connection failed", (viewModel.authState.value as AuthState.Error).message)
    }
}
