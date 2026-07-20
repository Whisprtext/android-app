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
    private val authInterceptor = Interceptor { chain ->
        val token = runBlocking { preferencesManager.sessionToken.first() }
        val request = chain.request().newBuilder()
        if (!token.isNullOrEmpty()) {
            request.addHeader("Authorization", "Bearer $token")
        }
        chain.proceed(request.build())
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        // Key registration + remote Neon can exceed default 10s under load
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Separate client without auth interceptor for direct S3/R2 presigned URL requests
    private val storageClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
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
        return sendMessage(conversationId, content, null, null, 0, null, null)
    }

    suspend fun sendMessage(
        conversationId: String,
        content: String,
        messageId: String? = null,
        recipientDeviceId: String? = null,
        messageType: Int = 0,
        attachment: AttachmentDto? = null,
        attachments: List<AttachmentDto>? = null
    ): MessageDto {
        val params = mutableMapOf<String, Any>()
        params["content"] = content
        if (messageId != null) params["id"] = messageId
        if (recipientDeviceId != null) params["recipient_device_id"] = recipientDeviceId
        if (messageType != 0) params["message_type"] = messageType
        if (attachment != null) params["attachment"] = attachment
        if (attachments != null) params["attachments"] = attachments

        val json = gson.toJson(params)
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
        displayName: String? = null,
        phoneNumberVisibility: String? = null
    ): UserDto {
        val json = gson.toJson(UpdateSettingsRequest(phoneNumber, discoverableByUsername, discoverableByPhone, displayName, phoneNumberVisibility))
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/me/settings")
            .put(body)
            .build()

        return executeRequest(request)
    }

    suspend fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        avatarUrl: String = ""
    ): UserDto {
        val json = gson.toJson(UpdateProfileRequest(username, displayName, bio, avatarUrl))
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/me/profile")
            .put(body)
            .build()

        return executeRequest(request)
    }

    suspend fun initAvatarUpload(mimeType: String, sizeBytes: Long): AvatarUploadInitResponse {
        val json = gson.toJson(AvatarUploadInitRequest(mimeType, sizeBytes))
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/me/avatar/upload/init")
            .post(body)
            .build()
        return executeRequest(request)
    }

    suspend fun setAvatar(
        fileId: String,
        fileUrl: String,
        mimeType: String,
        sizeBytes: Long
    ): UserDto {
        val json = gson.toJson(SetAvatarRequest(fileId, fileUrl, mimeType, sizeBytes))
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/me/avatar")
            .put(body)
            .build()
        return executeRequest(request)
    }

    suspend fun removeAvatar(): UserDto {
        val request = Request.Builder()
            .url("$baseUrl/me/avatar")
            .delete()
            .build()
        return executeRequest(request)
    }

    suspend fun getAvatarDownloadUrl(fileUrl: String): MediaDownloadResponse {
        val encodedUrl = java.net.URLEncoder.encode(fileUrl, "UTF-8")
        val request = Request.Builder()
            .url("$baseUrl/media/avatar?file_url=$encodedUrl")
            .get()
            .build()
        return executeRequest(request)
    }

    /** Upload raw avatar bytes to a presigned URL with the correct content type. */
    suspend fun uploadAvatarFile(uploadUrl: String, bytes: ByteArray, mimeType: String): Boolean =
        withContext(Dispatchers.IO) {
            val mediaType = mimeType.toMediaType()
            val body = bytes.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(uploadUrl)
                .put(body)
                .build()
            storageClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }

    suspend fun updatePushToken(token: String): Boolean {
        val json = gson.toJson(mapOf("push_token" to token))
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/me/push-token")
            .post(body)
            .build()
        return executeStatusRequest(request)
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

    suspend fun sendReceipt(messageId: String, status: String): Boolean {
        val json = gson.toJson(mapOf("status" to status))
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/messages/$messageId/receipts")
            .post(body)
            .build()
        return executeStatusRequest(request)
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

    suspend fun initMediaUpload(mimeType: String, sizeBytes: Long): MediaUploadInitResponse {
        val json = gson.toJson(MediaUploadInitRequest(mimeType, sizeBytes))
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/media/upload/init")
            .post(body)
            .build()
        return executeRequest(request)
    }

    suspend fun completeMediaUpload(fileId: String, fileUrl: String): Boolean {
        val json = gson.toJson(MediaUploadCompleteRequest(fileId, fileUrl))
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/media/upload/complete")
            .post(body)
            .build()
        return executeStatusRequest(request)
    }

    suspend fun getMediaDownloadUrl(fileUrl: String): MediaDownloadResponse {
        val encodedUrl = java.net.URLEncoder.encode(fileUrl, "UTF-8")
        val request = Request.Builder()
            .url("$baseUrl/media/download?file_url=$encodedUrl")
            .get()
            .build()
        return executeRequest(request)
    }

    suspend fun uploadEncryptedFile(uploadUrl: String, encryptedBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val mediaType = "application/octet-stream".toMediaType()
        val body = encryptedBytes.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(uploadUrl)
            .put(body)
            .build()
        storageClient.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }

    suspend fun downloadEncryptedFile(downloadUrl: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(downloadUrl)
            .get()
            .build()
        storageClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code ${response.code} during media download")
            }
            response.body?.bytes() ?: throw IOException("Empty media download body")
        }
    }

    suspend fun executePostRaw(path: String, bodyMap: Any): Boolean {
        val json = gson.toJson(bodyMap)
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/$path")
            .post(body)
            .build()
        return executeStatusRequest(request)
    }

    suspend fun executeGetRaw(path: String): String? {
        val request = Request.Builder()
            .url("$baseUrl/api/$path")
            .get()
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }
    }

    /**
     * Register this device's Signal public keys.
     * Server path: POST /api/keys/register (device id taken from auth session).
     * Uses a longer call timeout because the payload includes a batch of one-time prekeys.
     */
    suspend fun registerSignalKeys(bodyMap: Any): Boolean {
        val json = gson.toJson(bodyMap)
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/keys/register")
            .post(body)
            .build()
        return withContext(Dispatchers.IO) {
            // Explicit per-call timeout override (registration is bulkier than chat)
            val longCallClient = client.newBuilder()
                .callTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            longCallClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.e(
                        "SignalE2EE",
                        "keys_register_http status=${response.code} bodyLen=${response.body?.contentLength() ?: -1}"
                    )
                }
                response.isSuccessful
            }
        }
    }

    /**
     * Fetch prekey bundles for all devices of a user (identity + signed prekey only).
     * Does not claim one-time prekeys. Server path: GET /api/keys/users/{userId}
     */
    suspend fun getPreKeyBundles(userId: String): String? {
        val request = Request.Builder()
            .url("$baseUrl/api/keys/users/$userId")
            .get()
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }
    }

    data class ClaimedOneTimePreKey(
        @com.google.gson.annotations.SerializedName("id") val id: String = "",
        @com.google.gson.annotations.SerializedName("device_id") val deviceId: String = "",
        @com.google.gson.annotations.SerializedName("prekey_id") val preKeyId: Int = 0,
        @com.google.gson.annotations.SerializedName("public_key") val publicKey: String = ""
    )

    /**
     * Atomically claim one-time prekeys for the given device IDs.
     * Server path: POST /api/keys/claim
     * Returns map deviceId -> claimed key (missing entry if none left).
     */
    suspend fun claimOneTimePreKeys(deviceIds: List<String>): Map<String, ClaimedOneTimePreKey> {
        if (deviceIds.isEmpty()) return emptyMap()
        val json = gson.toJson(mapOf("device_ids" to deviceIds))
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/keys/claim")
            .post(body)
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.e("SignalE2EE", "otpk_claim_http status=${response.code}")
                    return@use emptyMap()
                }
                val bodyStr = response.body?.string() ?: return@use emptyMap()
                val type = object : com.google.gson.reflect.TypeToken<Map<String, ClaimedOneTimePreKey>>() {}.type
                try {
                    gson.fromJson<Map<String, ClaimedOneTimePreKey>>(bodyStr, type) ?: emptyMap()
                } catch (e: Exception) {
                    android.util.Log.e("SignalE2EE", "otpk_claim_parse_failed ${e.message}")
                    emptyMap()
                }
            }
        }
    }

    /** Ensure device UUID is known (e.g. after app update while already logged in). */
    suspend fun fetchMeDeviceId(): String? {
        return try {
            val me = getMe()
            me.device.id
        } catch (_: Exception) {
            null
        }
    }

    suspend fun sendQueueMessage(
        clientMessageId: String,
        recipientUserId: String,
        recipientDeviceId: String,
        ciphertext: String,
        ciphertextType: String = "prekey",
        protocolVersion: Int = 1,
        conversationId: String? = null
    ): QueueMessageResponse? {
        val params = mutableMapOf<String, Any?>(
            "client_message_id" to clientMessageId,
            "recipient_user_id" to recipientUserId,
            "recipient_device_id" to recipientDeviceId,
            "ciphertext" to ciphertext,
            "ciphertext_type" to ciphertextType,
            "protocol_version" to protocolVersion
        )
        if (conversationId != null) params["conversation_id"] = conversationId
        val json = gson.toJson(params)
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/messages")
            .post(body)
            .build()
        return try {
            executeRequest<QueueMessageResponse>(request)
        } catch (e: Exception) {
            android.util.Log.e("QueueAPI", "sendQueueMessage failed: ${e.message?.take(60)}")
            null
        }
    }

    suspend fun getPendingQueueMessages(): List<QueuePendingMessageDto> {
        val request = Request.Builder()
            .url("$baseUrl/messages/pending")
            .get()
            .build()
        val type = object : com.google.gson.reflect.TypeToken<QueuePendingResponse>() {}.type
        return try {
            val resp = executeRequest<QueuePendingResponse>(request, type)
            resp.messages
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun acknowledgeQueueMessage(messageId: String, clientMessageId: String, recipientDeviceId: String, status: String = "received"): Boolean {
        val params = mapOf(
            "client_message_id" to clientMessageId,
            "recipient_device_id" to recipientDeviceId,
            "status" to status
        )
        val json = gson.toJson(params)
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/messages/$messageId/ack")
            .post(body)
            .build()
        return executeStatusRequest(request)
    }

    suspend fun syncQueueMessages(): List<QueuePendingMessageDto> {
        val request = Request.Builder()
            .url("$baseUrl/messages/sync")
            .get()
            .build()
        val type = object : com.google.gson.reflect.TypeToken<QueuePendingResponse>() {}.type
        return try {
            val resp = executeRequest<QueuePendingResponse>(request, type)
            resp.messages
        } catch (e: Exception) {
            emptyList()
        }
    }

    data class QueueMessageResponse(
        val id: String,
        val client_message_id: String,
        val status: String,
        val created_at: String = ""
    )

    data class QueuePendingMessageDto(
        val id: String,
        val client_message_id: String,
        val sender_user_id: String,
        val sender_device_id: String,
        val recipient_device_id: String,
        val ciphertext: String,
        val ciphertext_type: String,
        val protocol_version: Int = 1,
        val conversation_id: String? = null,
        val created_at: String = ""
    )

    data class QueuePendingResponse(
        val messages: List<QueuePendingMessageDto>
    )

    private suspend fun executeStatusRequest(request: Request): Boolean = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }
}
