package com.whisprtext.app.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.FieldNamingPolicy
import com.google.gson.reflect.TypeToken
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.remote.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ApiClient(
    private val baseUrl: String,
    private val preferencesManager: PreferencesManager
) {
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val token = runBlocking { preferencesManager.sessionToken.first() }
            val request = chain.request().newBuilder()
            if (!token.isNullOrEmpty()) {
                request.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(request.build())
        }
        .build()

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun signup(username: String, passwordHash: String, deviceName: String): AuthResponse {
        val json = gson.toJson(mapOf("username" to username, "password" to passwordHash, "device_name" to deviceName))
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/auth/signup")
            .post(body)
            .build()

        return executeRequest(request)
    }

    suspend fun login(username: String, passwordHash: String, deviceName: String): AuthResponse {
        val json = gson.toJson(mapOf("username" to username, "password" to passwordHash, "device_name" to deviceName))
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/auth/login")
            .post(body)
            .build()

        return executeRequest(request)
    }

    suspend fun logout(): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/auth/logout")
            .post(FormBody.Builder().build())
            .build()

        return executeStatusRequest(request)
    }

    suspend fun createConversation(type: String, members: List<String>): ConversationDto {
        val json = gson.toJson(mapOf("type" to type, "members" to members))
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/conversations")
            .post(body)
            .build()

        return executeRequest(request)
    }

    suspend fun createDirectConversation(targetUserId: String?, username: String?): ConversationDto {
        val params = mutableMapOf<String, String>()
        if (targetUserId != null) params["target_user_id"] = targetUserId
        if (username != null) params["username"] = username
        val json = gson.toJson(params)
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/conversations/direct")
            .post(body)
            .build()

        return executeRequest(request)
    }

    suspend fun getConversations(cursor: String? = null, limit: Int? = null): List<ConversationSummaryDto> {
        val urlBuilder = "$baseUrl/conversations".toHttpUrlOrNull()!!.newBuilder()
        if (cursor != null) urlBuilder.addQueryParameter("cursor", cursor)
        if (limit != null) urlBuilder.addQueryParameter("limit", limit.toString())

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build()

        val type = object : TypeToken<List<ConversationSummaryDto>>() {}.type
        return executeRequest(request, type)
    }

    suspend fun sendMessage(conversationId: String, content: String): MessageDto {
        val json = gson.toJson(mapOf("content" to content))
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/conversations/$conversationId/messages")
            .post(body)
            .build()

        return executeRequest(request)
    }

    suspend fun getMessages(conversationId: String, cursor: String? = null, direction: String? = null, limit: Int? = null): List<MessageDto> {
        val urlBuilder = "$baseUrl/conversations/$conversationId/messages".toHttpUrlOrNull()!!.newBuilder()
        if (cursor != null) urlBuilder.addQueryParameter("cursor", cursor)
        if (direction != null) urlBuilder.addQueryParameter("direction", direction)
        if (limit != null) urlBuilder.addQueryParameter("limit", limit.toString())

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build()

        val type = object : TypeToken<List<MessageDto>>() {}.type
        return executeRequest(request, type)
    }

    suspend fun sync(since: String? = null): DeltaSyncDto {
        val urlBuilder = "$baseUrl/sync".toHttpUrlOrNull()!!.newBuilder()
        if (since != null) urlBuilder.addQueryParameter("since", since)

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build()

        return executeRequest(request)
    }

    suspend fun searchUserByUsername(username: String): UserDto {
        val urlBuilder = "$baseUrl/users/search".toHttpUrlOrNull()!!.newBuilder()
        urlBuilder.addQueryParameter("username", username)

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build()

        return executeRequest(request)
    }

    suspend fun lookupUsersByPhone(phoneNumbers: List<String>): List<UserDto> {
        val json = gson.toJson(PhoneLookupRequest(phoneNumbers))
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/users/lookup-by-phone")
            .post(body)
            .build()

        val type = object : TypeToken<List<UserDto>>() {}.type
        return executeRequest(request, type)
    }

    suspend fun updateSettings(
        phoneNumber: String?,
        discoverableByUsername: Boolean,
        discoverableByPhone: Boolean,
        displayName: String? = null
    ): UserDto {
        val json = gson.toJson(UpdateSettingsRequest(phoneNumber, discoverableByUsername, discoverableByPhone, displayName))
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/me/settings")
            .put(body)
            .build()

        return executeRequest(request)
    }

    suspend fun getMe(): MeResponse {
        val request = Request.Builder()
            .url("$baseUrl/me")
            .get()
            .build()

        return executeRequest(request)
    }

    private suspend inline fun <reified T> executeRequest(request: Request, type: java.lang.reflect.Type? = null): T = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code ${response.code} with body: ${response.body?.string()}")
            }
            val body = response.body?.string() ?: throw IOException("Empty response body")
            val result: T? = if (type != null) {
                gson.fromJson(body, type)
            } else {
                gson.fromJson(body, T::class.java)
            }
            result ?: throw IOException("Parsed response is null")
        }
    }

    suspend fun deleteMessageForEveryone(messageId: String): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/messages/$messageId")
            .delete()
            .build()
        return executeStatusRequest(request)
    }

    suspend fun deleteConversation(conversationId: String): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/conversations/$conversationId")
            .delete()
            .build()
        return executeStatusRequest(request)
    }

    suspend fun deleteAllConversations(): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/conversations")
            .delete()
            .build()
        return executeStatusRequest(request)
    }

    private suspend fun executeStatusRequest(request: Request): Boolean = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }
}
