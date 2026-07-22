package com.whisprtext.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Local translation entity caching translated message outputs per message ID and target language.
 *
 * Requirements:
 * - Original message text remains unchanged in [MessageEntity].
 * - Primary key is a composite of (messageId, targetLanguage).
 * - Foreign key deletes cached translations automatically when local message is deleted.
 * - Cached translations are stored locally only and never uploaded to backend servers.
 */
@Entity(
    tableName = "translations",
    primaryKeys = ["messageId", "targetLanguage", "sourceLanguage", "modelVersion"],
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["messageId"]),
    ],
)
data class TranslationEntity(
    val messageId: String,
    val targetLanguage: String,
    val sourceLanguage: String,
    val translatedText: String,
    val modelVersion: String,
    val createdAt: Long = System.currentTimeMillis(),
)
