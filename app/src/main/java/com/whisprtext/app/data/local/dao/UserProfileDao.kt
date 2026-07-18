package com.whisprtext.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whisprtext.app.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(profiles: List<UserProfileEntity>)

    @Query("SELECT * FROM user_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE username = :username LIMIT 1")
    suspend fun getByUsername(username: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE username = :username LIMIT 1")
    fun observeByUsername(username: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE isSelf = 1 LIMIT 1")
    suspend fun getSelf(): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE isSelf = 1 LIMIT 1")
    fun observeSelf(): Flow<UserProfileEntity?>

    @Query("DELETE FROM user_profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM user_profiles")
    suspend fun deleteAll()
}
