package com.whisprtext.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.whisprtext.app.data.local.dao.ConversationDao
import com.whisprtext.app.data.local.dao.MessageDao
import com.whisprtext.app.data.local.dao.PendingReceiptDao
import com.whisprtext.app.data.local.dao.UserProfileDao
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.local.entity.PendingReceiptEntity
import com.whisprtext.app.data.local.entity.UserProfileEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        PendingReceiptEntity::class,
        UserProfileEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun pendingReceiptDao(): PendingReceiptDao
    abstract fun userProfileDao(): UserProfileDao
}
