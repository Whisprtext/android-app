package com.whisprtext.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.whisprtext.app.data.local.AppDatabase
import com.whisprtext.app.data.local.dao.ConversationDao
import com.whisprtext.app.data.local.dao.MessageDao
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class RoomRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var conversationDao: ConversationDao
    private lateinit var messageDao: MessageDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        conversationDao = db.conversationDao()
        messageDao = db.messageDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun writeAndReadConversation() = runBlocking {
        val conversation = ConversationEntity(
            id = "conv-123",
            type = "direct",
            createdAt = System.currentTimeMillis(),
            unreadCount = 5,
            lastMessageText = "Hello World",
            lastMessageTime = System.currentTimeMillis()
        )
        conversationDao.insert(conversation)

        val list = conversationDao.getConversationsFlow().first()
        assertEquals(1, list.size)
        assertEquals("conv-123", list[0].id)
        assertEquals(5, list[0].unreadCount)
    }

    @Test
    fun writeAndReadMessages() = runBlocking {
        val msg = MessageEntity(
            id = "msg-1",
            conversationId = "conv-123",
            senderId = "user-1",
            senderDeviceId = "dev-1",
            encryptedContent = "Hey",
            createdAt = System.currentTimeMillis(),
            syncStatus = "sent"
        )
        messageDao.insert(msg)

        val list = messageDao.getMessagesForConversation("conv-123").first()
        assertEquals(1, list.size)
        assertEquals("msg-1", list[0].id)
        assertEquals("Hey", list[0].encryptedContent)
    }
}
