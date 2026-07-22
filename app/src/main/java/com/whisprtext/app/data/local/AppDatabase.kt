package com.whisprtext.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.whisprtext.app.data.local.dao.ConversationDao
import com.whisprtext.app.data.local.dao.MessageDao
import com.whisprtext.app.data.local.dao.OutboxDao
import com.whisprtext.app.data.local.dao.PendingReceiptDao
import com.whisprtext.app.data.local.dao.TranslationDao
import com.whisprtext.app.data.local.dao.UserProfileDao
import com.whisprtext.app.data.local.entity.ConversationEntity
import com.whisprtext.app.data.local.entity.MessageEntity
import com.whisprtext.app.data.local.entity.OutboxEntity
import com.whisprtext.app.data.local.entity.PendingReceiptEntity
import com.whisprtext.app.data.local.entity.TranslationEntity
import com.whisprtext.app.data.local.entity.UserProfileEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        OutboxEntity::class,
        PendingReceiptEntity::class,
        UserProfileEntity::class,
        TranslationEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun outboxDao(): OutboxDao
    abstract fun pendingReceiptDao(): PendingReceiptDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun translationDao(): TranslationDao
}
