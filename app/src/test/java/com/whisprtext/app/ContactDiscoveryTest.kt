package com.whisprtext.app

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.provider.ContactsContract
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.remote.model.UserDto
import com.whisprtext.app.data.repository.ChatRepository
import com.whisprtext.app.ui.viewmodel.ContactDiscoveryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ContactDiscoveryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val chatRepository: ChatRepository = mock()
    private val context: Context = mock()
    private val contentResolver: ContentResolver = mock()

    private lateinit var viewModel: ContactDiscoveryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(context.contentResolver).thenReturn(contentResolver)
        viewModel = ContactDiscoveryViewModel(chatRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testNormalizePhone() {
        assertEquals("+15555551234", viewModel.normalizePhone("+1 (555) 555-1234"))
        assertEquals("+15555551234", viewModel.normalizePhone("1-555-555-1234"))
        assertEquals(null, viewModel.normalizePhone("123"))
    }

    @Test
    fun testSearchUserSuccess() = runTest {
        val userDto = UserDto("uuid-bob", "bob", "+15555551234", true, true)
        whenever(chatRepository.searchUserByUsername("bob")).thenReturn(userDto)

        viewModel.searchUser("bob")
        runCurrent()

        assertEquals(userDto, viewModel.searchResult.value)
    }

    @Test
    fun testSearchUserFailure() = runTest {
        whenever(chatRepository.searchUserByUsername("nonexistent")).thenAnswer {
            throw Exception("User not found")
        }

        var errorMsg: String? = null
        val job = launch {
            viewModel.error.collect {
                errorMsg = it
            }
        }

        viewModel.searchUser("nonexistent")
        runCurrent()

        assertNotNull(errorMsg)
        job.cancel()
    }

    @Test
    fun testSyncContactsAndDiscoverOption() = runTest {
        val cursor = MatrixCursor(arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ))
        cursor.addRow(arrayOf("Charlie", "+15555554321"))
        cursor.addRow(arrayOf("David", "+15555558888"))

        whenever(contentResolver.query(any(), any(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(cursor)

        val discoverableUser = UserDto("uuid-charlie", "charlie", "+15555554321", true, true)
        whenever(chatRepository.lookupUsersByPhone(any())).thenReturn(listOf(discoverableUser))

        viewModel.syncContacts(context)
        runCurrent()

        val matched = viewModel.matchedContacts.value
        assertEquals(1, matched.size)
        assertEquals("charlie", matched[0].username)

        val unmatched = viewModel.unmatchedContacts.value
        assertEquals(1, unmatched.size)
        assertEquals("David", unmatched[0].name)
    }

    @Test
    fun testStartChatSuccess() = runTest {
        val userDto = UserDto("uuid-bob", "bob", "+15555551234", true, true)
        val conversationEntity = ConversationEntity("conv-uuid", "direct", System.currentTimeMillis(), 0, null, null)
        whenever(chatRepository.createDirectConversation(eq("uuid-bob"), anyOrNull(), anyOrNull())).thenReturn(conversationEntity)

        var createdConv: ConversationEntity? = null
        val job = launch {
            viewModel.chatCreated.collect {
                createdConv = it
            }
        }

        viewModel.startChat(userDto)
        runCurrent()

        assertNotNull(createdConv)
        assertEquals("conv-uuid", createdConv?.id)
        job.cancel()
    }
}
