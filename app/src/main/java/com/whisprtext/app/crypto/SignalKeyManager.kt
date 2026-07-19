package com.whisprtext.app.crypto

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.whisprtext.app.data.local.PreferencesManager
import com.whisprtext.app.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.SessionBuilder
import org.whispersystems.libsignal.SessionCipher
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.protocol.CiphertextMessage
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SignalMessage
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.libsignal.util.KeyHelper
import java.util.concurrent.ConcurrentHashMap

/**
 * Signal Protocol key management, session establishment, encrypt, and decrypt.
 *
 * Wire envelope:
 * - content: base64(Signal ciphertext) once
 * - message_type: 2 (whisper) or 3 (prekey)
 * - recipient_device_id / sender_device_id: server device UUIDs
 *
 * OTPK claim policy:
 * - Listing peer devices must NOT claim one-time prekeys
 * - Claim exactly one OTPK only when building a new session for that device
 */
class SignalKeyManager(
    private val context: Context,
    private val apiClient: ApiClient,
    private val preferencesManager: PreferencesManager
) {
    val store = SignalStoreImpl(context)
    private val gson = Gson()
    private val registerMutex = Mutex()
    private val sessionLocks = ConcurrentHashMap<String, Mutex>()

    data class EncryptedMessageEnvelope(
        val ciphertext: String,
        val messageType: Int,
        val ciphertextByteLength: Int,
        val protocolVersion: Int = PROTOCOL_VERSION
    )

    data class DecryptedPayload(
        val text: String?,
        val attachments: List<AttachmentPayloadDto>? = null
    )

    data class AttachmentPayloadDto(
        @SerializedName("object_key") val objectKey: String = "",
        @SerializedName("attachment_key") val attachmentKey: String = "",
        val nonce: String = "",
        val digest: String = "",
        @SerializedName("mime_type") val mimeType: String = "",
        @SerializedName("size_bytes") val sizeBytes: Long = 0L
    )

    data class PreKeyBundleDto(
        @SerializedName("device_id") val deviceId: String = "",
        @SerializedName("registration_id") val registrationId: Int = 0,
        @SerializedName("identity_key") val identityKey: String = "",
        @SerializedName("signed_prekey_id") val signedPreKeyId: Int = 0,
        @SerializedName("signed_prekey_public") val signedPreKeyPublic: String = "",
        @SerializedName("signed_prekey_signature") val signedPreKeySignature: String = "",
        @SerializedName("onetime_prekey_id") val oneTimePreKeyId: Int? = null,
        @SerializedName("onetime_prekey_public") val oneTimePreKeyPublic: String? = null
    )

    private fun sessionLockKey(userId: String, deviceUuid: String) = "$userId|$deviceUuid"

    private fun sessionMutex(userId: String, deviceUuid: String): Mutex {
        return sessionLocks.getOrPut(sessionLockKey(userId, deviceUuid)) { Mutex() }
    }

    suspend fun registerDeviceKeysIfNecessary(userId: String, deviceId: String) {
        val flagKey = "signal_keys_registered_${userId}_$deviceId"
        if (preferencesManager.getSecureValue(flagKey) != null &&
            store.containsSignedPreKey(SIGNED_PREKEY_ID) &&
            store.containsPreKey(1)
        ) {
            logCrypto("keys_already_registered", mapOf("userId" to userId, "deviceId" to deviceId))
            return
        }

        registerMutex.withLock {
            if (preferencesManager.getSecureValue(flagKey) != null &&
                store.containsSignedPreKey(SIGNED_PREKEY_ID) &&
                store.containsPreKey(1)
            ) {
                return
            }

            withContext(NonCancellable + Dispatchers.IO) {
                val identityKeyPair = store.identityKeyPair
                val registrationId = store.localRegistrationId

                // Reuse existing local keys when re-uploading so in-flight prekey
                // sessions are not invalidated by regenerating the same prekey IDs.
                val signedPreKey = if (store.containsSignedPreKey(SIGNED_PREKEY_ID)) {
                    store.loadSignedPreKey(SIGNED_PREKEY_ID)
                } else {
                    KeyHelper.generateSignedPreKey(identityKeyPair, SIGNED_PREKEY_ID).also {
                        store.storeSignedPreKey(SIGNED_PREKEY_ID, it)
                    }
                }

                val oneTimePreKeys = mutableListOf<org.whispersystems.libsignal.state.PreKeyRecord>()
                var needGenerate = false
                for (id in 1..OTPK_REGISTER_COUNT) {
                    if (store.containsPreKey(id)) {
                        try {
                            oneTimePreKeys.add(store.loadPreKey(id))
                        } catch (_: Exception) {
                            needGenerate = true
                            break
                        }
                    } else {
                        needGenerate = true
                        break
                    }
                }
                if (needGenerate || oneTimePreKeys.size < OTPK_REGISTER_COUNT) {
                    oneTimePreKeys.clear()
                    val generated = KeyHelper.generatePreKeys(1, OTPK_REGISTER_COUNT)
                    for (preKey in generated) {
                        store.storePreKey(preKey.id, preKey)
                        oneTimePreKeys.add(preKey)
                    }
                }

                val requestPayload = mapOf(
                    "registration_id" to registrationId,
                    "identity_key" to Base64Compat.encode(identityKeyPair.publicKey.serialize()),
                    "signed_prekey" to mapOf(
                        "id" to SIGNED_PREKEY_ID,
                        "public_key" to Base64Compat.encode(signedPreKey.keyPair.publicKey.serialize()),
                        "signature" to Base64Compat.encode(signedPreKey.signature)
                    ),
                    "one_time_prekeys" to oneTimePreKeys.map {
                        mapOf(
                            "id" to it.id,
                            "public_key" to Base64Compat.encode(it.keyPair.publicKey.serialize())
                        )
                    }
                )

                logCrypto(
                    "keys_register_start",
                    mapOf(
                        "userId" to userId,
                        "deviceId" to deviceId,
                        "oneTimePreKeyCount" to oneTimePreKeys.size,
                        "registrationId" to registrationId
                    )
                )

                val response = apiClient.registerSignalKeys(requestPayload)
                if (response) {
                    preferencesManager.saveSecureValue(flagKey, "true")
                    logCrypto(
                        "keys_registered",
                        mapOf(
                            "userId" to userId,
                            "deviceId" to deviceId,
                            "registrationId" to registrationId,
                            "oneTimePreKeyCount" to oneTimePreKeys.size
                        )
                    )
                } else {
                    logCrypto(
                        "keys_register_failed",
                        mapOf("userId" to userId, "deviceId" to deviceId),
                        isError = true
                    )
                    throw IllegalStateException("Failed to register Signal keys with server")
                }
            }
        }
    }

    /**
     * List peer devices' public identity + signed prekey (does not claim OTPKs).
     */
    suspend fun fetchPreKeyBundles(userId: String): List<PreKeyBundleDto> = withContext(Dispatchers.IO) {
        val response = apiClient.getPreKeyBundles(userId)
            ?: throw IllegalStateException("No prekey bundles for user $userId")
        val type = object : TypeToken<List<PreKeyBundleDto>>() {}.type
        val bundles: List<PreKeyBundleDto> = gson.fromJson(response, type) ?: emptyList()
        bundles.filter { it.deviceId.isNotBlank() && it.identityKey.isNotBlank() && it.signedPreKeyPublic.isNotBlank() }
    }

    suspend fun encryptMessage(
        recipientUserId: String,
        recipientDeviceId: String,
        plaintext: String,
        messageId: String? = null
    ): EncryptedMessageEnvelope = withContext(Dispatchers.IO) {
        require(recipientDeviceId.isNotBlank()) { "recipientDeviceId required" }
        sessionMutex(recipientUserId, recipientDeviceId).withLock {
            val address = toAddress(recipientUserId, recipientDeviceId)

            // Always load current public identity for this device; rebuild session if peer re-keyed
            val remoteBundle = fetchPreKeyBundles(recipientUserId)
                .find { it.deviceId == recipientDeviceId }
                ?: throw IllegalStateException("Prekey bundle not found for device: $recipientDeviceId")

            val remoteIdentity = IdentityKey(Base64Compat.decode(remoteBundle.identityKey), 0)
            val storedIdentity = store.getIdentity(address)
            val identityChanged = storedIdentity != null && storedIdentity != remoteIdentity
            val sessionExists = store.containsSession(address)

            if (identityChanged) {
                logCrypto(
                    "remote_identity_changed",
                    mapOf(
                        "messageId" to (messageId ?: ""),
                        "recipientDeviceId" to recipientDeviceId
                    )
                )
                store.deleteSession(address)
            }

            logCrypto(
                "encrypt_start",
                mapOf(
                    "messageId" to (messageId ?: ""),
                    "recipientUserId" to recipientUserId,
                    "recipientDeviceId" to recipientDeviceId,
                    "signalDeviceId" to address.deviceId,
                    "sessionExists" to sessionExists,
                    "identityChanged" to identityChanged,
                    "plaintextUtf8Length" to plaintext.toByteArray(Charsets.UTF_8).size
                )
            )

            if (!store.containsSession(address) || identityChanged) {
                processPreKeyBundle(remoteBundle, address)
            }

            try {
                encryptWithCurrentSession(address, plaintext, messageId, recipientDeviceId)
            } catch (e: Exception) {
                // Stale ratchet — force a fresh prekey session once
                logCrypto(
                    "encrypt_retry_fresh_session",
                    mapOf(
                        "messageId" to (messageId ?: ""),
                        "recipientDeviceId" to recipientDeviceId,
                        "reason" to e.javaClass.simpleName
                    ),
                    isError = true
                )
                store.deleteSession(address)
                val refreshed = fetchPreKeyBundles(recipientUserId)
                    .find { it.deviceId == recipientDeviceId }
                    ?: throw e
                processPreKeyBundle(refreshed, address)
                encryptWithCurrentSession(address, plaintext, messageId, recipientDeviceId)
            }
        }
    }

    private fun encryptWithCurrentSession(
        address: SignalProtocolAddress,
        plaintext: String,
        messageId: String?,
        recipientDeviceId: String
    ): EncryptedMessageEnvelope {
        val sessionCipher = SessionCipher(store, address)
        val ciphertextMessage = sessionCipher.encrypt(plaintext.toByteArray(Charsets.UTF_8))
        val ciphertextBytes = ciphertextMessage.serialize()
        val encoded = Base64Compat.encode(ciphertextBytes)
        val roundTrip = Base64Compat.decode(encoded)
        if (!roundTrip.contentEquals(ciphertextBytes)) {
            throw IllegalStateException("Base64 round-trip corrupted ciphertext")
        }
        logCrypto(
            "encrypt_success",
            mapOf(
                "messageId" to (messageId ?: ""),
                "recipientDeviceId" to recipientDeviceId,
                "messageType" to ciphertextMessage.type,
                "ciphertextByteLength" to ciphertextBytes.size,
                "encoding" to "base64",
                "protocolVersion" to PROTOCOL_VERSION
            )
        )
        return EncryptedMessageEnvelope(
            ciphertext = encoded,
            messageType = ciphertextMessage.type,
            ciphertextByteLength = ciphertextBytes.size
        )
    }

    suspend fun decryptMessage(
        senderUserId: String,
        senderDeviceId: String,
        ciphertextBase64: String,
        messageType: Int,
        messageId: String? = null,
        recipientDeviceId: String? = null
    ): String = withContext(Dispatchers.IO) {
        require(senderDeviceId.isNotBlank()) { "senderDeviceId required" }
        sessionMutex(senderUserId, senderDeviceId).withLock {
            val address = toAddress(senderUserId, senderDeviceId)
            val sessionExists = store.containsSession(address)
            val cleaned = ciphertextBase64.replace("\n", "").replace("\r", "").trim()
            val ciphertextBytes = try {
                Base64Compat.decode(cleaned)
            } catch (e: Exception) {
                logCrypto(
                    "decrypt_base64_failed",
                    mapOf(
                        "messageId" to (messageId ?: ""),
                        "senderDeviceId" to senderDeviceId,
                        "messageType" to messageType,
                        "reason" to (e.message ?: "decode_error")
                    ),
                    isError = true
                )
                throw e
            }

            logCrypto(
                "decrypt_start",
                mapOf(
                    "messageId" to (messageId ?: ""),
                    "senderUserId" to senderUserId,
                    "senderDeviceId" to senderDeviceId,
                    "recipientDeviceId" to (recipientDeviceId ?: ""),
                    "signalDeviceId" to address.deviceId,
                    "messageType" to messageType,
                    "ciphertextByteLength" to ciphertextBytes.size,
                    "encoding" to "base64",
                    "sessionExists" to sessionExists,
                    "protocolVersion" to PROTOCOL_VERSION
                )
            )

            val sessionCipher = SessionCipher(store, address)
            try {
                val decryptedBytes = decryptWithType(sessionCipher, ciphertextBytes, messageType)
                logCrypto(
                    "decrypt_success",
                    mapOf(
                        "messageId" to (messageId ?: ""),
                        "senderDeviceId" to senderDeviceId,
                        "messageType" to messageType,
                        "ciphertextByteLength" to ciphertextBytes.size,
                        "plaintextUtf8Length" to decryptedBytes.size
                    )
                )
                String(decryptedBytes, Charsets.UTF_8)
            } catch (first: Exception) {
                val alternate = alternateMessageType(messageType)
                if (alternate != null) {
                    try {
                        val decryptedBytes = decryptWithType(sessionCipher, ciphertextBytes, alternate)
                        logCrypto(
                            "decrypt_success_type_fallback",
                            mapOf(
                                "messageId" to (messageId ?: ""),
                                "triedType" to alternate,
                                "originalType" to messageType
                            )
                        )
                        return@withLock String(decryptedBytes, Charsets.UTF_8)
                    } catch (_: Exception) {
                        // continue
                    }
                }

                logCrypto(
                    "decrypt_failed",
                    mapOf(
                        "messageId" to (messageId ?: ""),
                        "senderDeviceId" to senderDeviceId,
                        "messageType" to messageType,
                        "ciphertextByteLength" to ciphertextBytes.size,
                        "sessionExists" to sessionExists,
                        "reason" to (first.javaClass.simpleName + ":" + (first.message?.take(120) ?: ""))
                    ),
                    isError = true
                )

                // Drop broken session so the *next* inbound prekey message can re-establish
                if (sessionExists) {
                    store.deleteSession(address)
                    logCrypto(
                        "session_reset",
                        mapOf(
                            "messageId" to (messageId ?: ""),
                            "senderDeviceId" to senderDeviceId,
                            "reason" to "decrypt_failure"
                        )
                    )
                }
                throw first
            }
        }
    }

    private fun decryptWithType(
        sessionCipher: SessionCipher,
        ciphertextBytes: ByteArray,
        messageType: Int
    ): ByteArray {
        return when (messageType) {
            CiphertextMessage.PREKEY_TYPE ->
                sessionCipher.decrypt(PreKeySignalMessage(ciphertextBytes))
            CiphertextMessage.WHISPER_TYPE ->
                sessionCipher.decrypt(SignalMessage(ciphertextBytes))
            else -> {
                try {
                    sessionCipher.decrypt(PreKeySignalMessage(ciphertextBytes))
                } catch (_: Exception) {
                    sessionCipher.decrypt(SignalMessage(ciphertextBytes))
                }
            }
        }
    }

    private fun alternateMessageType(messageType: Int): Int? {
        return when (messageType) {
            CiphertextMessage.PREKEY_TYPE -> CiphertextMessage.WHISPER_TYPE
            CiphertextMessage.WHISPER_TYPE -> CiphertextMessage.PREKEY_TYPE
            else -> null
        }
    }

    fun hasSession(remoteUserId: String, remoteDeviceId: String): Boolean {
        return store.containsSession(toAddress(remoteUserId, remoteDeviceId))
    }

    fun clearSession(remoteUserId: String, remoteDeviceId: String) {
        store.deleteSession(toAddress(remoteUserId, remoteDeviceId))
    }

    /**
     * Build a Double Ratchet session from a public bundle, claiming one OTPK for
     * this device only (if available).
     */
    private fun processPreKeyBundle(bundleDto: PreKeyBundleDto, address: SignalProtocolAddress) {
        if (bundleDto.identityKey.isBlank() || bundleDto.signedPreKeyPublic.isBlank()) {
            throw IllegalStateException("Incomplete prekey bundle for device: ${bundleDto.deviceId}")
        }

        val identityKey = IdentityKey(Base64Compat.decode(bundleDto.identityKey), 0)
        val signedPreKeyPublic = Curve.decodePoint(Base64Compat.decode(bundleDto.signedPreKeyPublic), 0)
        val signedPreKeySignature = Base64Compat.decode(bundleDto.signedPreKeySignature)

        // Claim a one-time prekey for THIS device only (list endpoint no longer claims)
        var oneTimePreKeyId = 0
        var oneTimePreKeyPublic: org.whispersystems.libsignal.ecc.ECPublicKey? = null
        var hasOtpk = false
        try {
            val claimed = kotlinx.coroutines.runBlocking {
                apiClient.claimOneTimePreKeys(listOf(bundleDto.deviceId))
            }
            val otk = claimed[bundleDto.deviceId]
            if (otk != null && otk.preKeyId > 0 && otk.publicKey.isNotBlank()) {
                oneTimePreKeyId = otk.preKeyId
                oneTimePreKeyPublic = Curve.decodePoint(Base64Compat.decode(otk.publicKey), 0)
                hasOtpk = true
            }
        } catch (e: Exception) {
            logCrypto(
                "otpk_claim_failed_using_signed_only",
                mapOf(
                    "deviceId" to bundleDto.deviceId,
                    "reason" to (e.javaClass.simpleName)
                )
            )
        }

        // Fall back to OTPK embedded in bundle if present (older servers)
        if (!hasOtpk &&
            bundleDto.oneTimePreKeyId != null &&
            bundleDto.oneTimePreKeyId > 0 &&
            !bundleDto.oneTimePreKeyPublic.isNullOrBlank()
        ) {
            oneTimePreKeyId = bundleDto.oneTimePreKeyId
            oneTimePreKeyPublic = Curve.decodePoint(Base64Compat.decode(bundleDto.oneTimePreKeyPublic), 0)
            hasOtpk = true
        }

        val preKeyBundle = PreKeyBundle(
            bundleDto.registrationId,
            address.deviceId,
            oneTimePreKeyId,
            oneTimePreKeyPublic,
            bundleDto.signedPreKeyId,
            signedPreKeyPublic,
            signedPreKeySignature,
            identityKey
        )

        if (store.containsSession(address)) {
            store.deleteSession(address)
        }
        // Persist peer identity (TOFU / rotation)
        store.saveIdentity(address, identityKey)

        val sessionBuilder = SessionBuilder(store, address)
        sessionBuilder.process(preKeyBundle)

        logCrypto(
            "session_established",
            mapOf(
                "recipientDeviceId" to bundleDto.deviceId,
                "signalDeviceId" to address.deviceId,
                "hasOneTimePreKey" to hasOtpk,
                "registrationId" to bundleDto.registrationId,
                "signedPreKeyId" to bundleDto.signedPreKeyId
            )
        )
    }

    companion object {
        const val PROTOCOL_VERSION = 1
        const val TAG = "SignalE2EE"
        const val DISPLAY_DECRYPT_FAILED = "Unable to decrypt this message"
        const val OTPK_REGISTER_COUNT = 50
        const val SIGNED_PREKEY_ID = 1

        fun signalDeviceId(deviceUuid: String): Int {
            val h = deviceUuid.hashCode() and 0x7FFFFFFF
            return if (h == 0) 1 else h
        }

        fun toAddress(userId: String, deviceUuid: String): SignalProtocolAddress {
            return SignalProtocolAddress(userId, signalDeviceId(deviceUuid))
        }

        fun isLikelyCiphertext(text: String): Boolean {
            if (text.isBlank() || text == DISPLAY_DECRYPT_FAILED) return false
            if (text == "[Decryption failed]" || text == "[Media]") return false
            val cleaned = text.replace("\n", "").replace("\r", "").trim()
            if (cleaned.length < 24) return false
            if (cleaned.any { it.isWhitespace() }) return false
            val base64Chars = cleaned.all {
                it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' || it == '-' || it == '_'
            }
            return base64Chars && cleaned.length >= 32
        }

        private fun logCrypto(event: String, fields: Map<String, Any?>, isError: Boolean = false) {
            val msg = buildString {
                append(event)
                fields.forEach { (k, v) ->
                    append(' ')
                    append(k)
                    append('=')
                    append(v)
                }
            }
            if (isError) Log.e(TAG, msg) else Log.d(TAG, msg)
        }
    }

    private fun logCrypto(event: String, fields: Map<String, Any?>, isError: Boolean = false) {
        Companion.logCrypto(event, fields, isError)
    }
}
