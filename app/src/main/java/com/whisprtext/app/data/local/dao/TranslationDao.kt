package com.whisprtext.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whisprtext.app.data.local.entity.TranslationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations WHERE messageId = :messageId AND targetLanguage = :targetLanguage AND sourceLanguage = :sourceLanguage AND modelVersion = :modelVersion LIMIT 1")
    suspend fun getTranslation(messageId: String, targetLanguage: String, sourceLanguage: String, modelVersion: String): TranslationEntity?

    @Query("SELECT * FROM translations WHERE messageId = :messageId AND targetLanguage = :targetLanguage LIMIT 1")
    suspend fun getTranslation(messageId: String, targetLanguage: String): TranslationEntity?

    @Query("SELECT * FROM translations WHERE messageId = :messageId AND targetLanguage = :targetLanguage AND sourceLanguage = :sourceLanguage AND modelVersion = :modelVersion LIMIT 1")
    fun observeTranslation(messageId: String, targetLanguage: String, sourceLanguage: String, modelVersion: String): Flow<TranslationEntity?>

    @Query("SELECT * FROM translations WHERE messageId = :messageId AND targetLanguage = :targetLanguage LIMIT 1")
    fun observeTranslation(messageId: String, targetLanguage: String): Flow<TranslationEntity?>

    @Query("SELECT * FROM translations WHERE messageId IN (:messageIds) AND targetLanguage = :targetLanguage")
    suspend fun getTranslationsForMessages(messageIds: List<String>, targetLanguage: String): List<TranslationEntity>

    @Query("SELECT * FROM translations WHERE messageId IN (:messageIds) AND targetLanguage = :targetLanguage")
    fun observeTranslationsForMessages(messageIds: List<String>, targetLanguage: String): Flow<List<TranslationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslation(translation: TranslationEntity)

    @Query("DELETE FROM translations WHERE messageId = :messageId")
    suspend fun deleteTranslationsForMessage(messageId: String)

    @Query("DELETE FROM translations")
    suspend fun clearAllTranslations()
}
