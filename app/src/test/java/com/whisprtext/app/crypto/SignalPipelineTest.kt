package com.whisprtext.app.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.whispersystems.libsignal.SessionBuilder
import org.whispersystems.libsignal.SessionCipher
import org.whispersystems.libsignal.protocol.CiphertextMessage
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SignalMessage
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.libsignal.util.KeyHelper

/**
 * End-to-end Signal encrypt → base64 → deserialize → decrypt tests
 * using two local stores (Alice / Bob) without a network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SignalPipelineTest {

    private lateinit var context: Context
    private lateinit var aliceStore: SignalStoreImpl
    private lateinit var bobStore: SignalStoreImpl

    private val aliceUserId = "alice-user-uuid"
    private val bobUserId = "bob-user-uuid"
    private val aliceDeviceUuid = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    private val bobDeviceUuid = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        aliceStore = SignalStoreImpl(context, "signal_alice")
        bobStore = SignalStoreImpl(context, "signal_bob")
        aliceStore.clearLocalData()
        bobStore.clearLocalData()

        assertNotNull(aliceStore.identityKeyPair)
        assertNotNull(bobStore.identityKeyPair)
        assertNotEquals(
            Base64Compat.encode(aliceStore.identityKeyPair.publicKey.serialize()),
            Base64Compat.encode(bobStore.identityKeyPair.publicKey.serialize())
        )
    }

    private fun installBobPreKeys(): PreKeyBundle {
        val signedPreKeyId = 1
        val signedPreKey = KeyHelper.generateSignedPreKey(bobStore.identityKeyPair, signedPreKeyId)
        bobStore.storeSignedPreKey(signedPreKeyId, signedPreKey)

        val preKeys = KeyHelper.generatePreKeys(1, 5)
        for (pk in preKeys) bobStore.storePreKey(pk.id, pk)
        val otpk = preKeys[0]

        return PreKeyBundle(
            bobStore.localRegistrationId,
            SignalKeyManager.signalDeviceId(bobDeviceUuid),
            otpk.id,
            otpk.keyPair.publicKey,
            signedPreKeyId,
            signedPreKey.keyPair.publicKey,
            signedPreKey.signature,
            bobStore.identityKeyPair.publicKey
        )
    }

    @Test
    fun base64RoundTrip_preservesBytes() {
        val original = ByteArray(64) { it.toByte() }
        val encoded = Base64Compat.encode(original)
        val decoded = Base64Compat.decode(encoded)
        assertArrayEquals(original, decoded)

        val messy = encoded.chunked(16).joinToString("\n")
        assertArrayEquals(original, Base64Compat.decode(messy))
    }

    @Test
    fun firstMessage_prekeySession_encryptDecrypt() {
        val bobBundle = installBobPreKeys()
        val aliceToBob = SignalKeyManager.toAddress(bobUserId, bobDeviceUuid)

        SessionBuilder(aliceStore, aliceToBob).process(bobBundle)

        val plaintext = """{"text":"hello e2ee","attachments":null}"""
        val cipher = SessionCipher(aliceStore, aliceToBob)
        val encrypted = cipher.encrypt(plaintext.toByteArray(Charsets.UTF_8))
        assertEquals(CiphertextMessage.PREKEY_TYPE, encrypted.type)

        val wire = Base64Compat.encode(encrypted.serialize())
        val receivedBytes = Base64Compat.decode(wire)
        assertArrayEquals(encrypted.serialize(), receivedBytes)

        val bobFromAlice = SignalKeyManager.toAddress(aliceUserId, aliceDeviceUuid)
        val bobCipher = SessionCipher(bobStore, bobFromAlice)
        val decrypted = bobCipher.decrypt(PreKeySignalMessage(receivedBytes))
        assertEquals(plaintext, String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun existingSession_whisperMessage_encryptDecrypt() {
        val bobBundle = installBobPreKeys()
        val aliceToBob = SignalKeyManager.toAddress(bobUserId, bobDeviceUuid)
        SessionBuilder(aliceStore, aliceToBob).process(bobBundle)

        val aliceCipher = SessionCipher(aliceStore, aliceToBob)
        val bobFromAlice = SignalKeyManager.toAddress(aliceUserId, aliceDeviceUuid)
        val bobCipher = SessionCipher(bobStore, bobFromAlice)

        // 1) Alice → Bob (prekey / first message)
        val first = aliceCipher.encrypt("first".toByteArray())
        assertEquals(CiphertextMessage.PREKEY_TYPE, first.type)
        bobCipher.decrypt(PreKeySignalMessage(first.serialize()))

        // 2) Bob → Alice (acks the session so Alice can switch to whisper)
        val reply = bobCipher.encrypt("ack".toByteArray())
        val replyWire = Base64Compat.encode(reply.serialize())
        // Bob's first outbound after receiving prekey is a normal SignalMessage
        aliceCipher.decrypt(SignalMessage(Base64Compat.decode(replyWire)))

        // 3) Subsequent Alice → Bob should be whisper-type on an established session
        val second = aliceCipher.encrypt("second message".toByteArray())
        assertEquals(CiphertextMessage.WHISPER_TYPE, second.type)
        val wire = Base64Compat.encode(second.serialize())
        val plain = bobCipher.decrypt(SignalMessage(Base64Compat.decode(wire)))
        assertEquals("second message", String(plain, Charsets.UTF_8))
    }

    @Test
    fun sessionSurvivesStoreReload() {
        val bobBundle = installBobPreKeys()
        val aliceToBob = SignalKeyManager.toAddress(bobUserId, bobDeviceUuid)
        SessionBuilder(aliceStore, aliceToBob).process(bobBundle)

        val aliceCipher = SessionCipher(aliceStore, aliceToBob)
        val bobFromAlice = SignalKeyManager.toAddress(aliceUserId, aliceDeviceUuid)
        val bobCipher = SessionCipher(bobStore, bobFromAlice)

        val first = aliceCipher.encrypt("bootstrap".toByteArray())
        bobCipher.decrypt(PreKeySignalMessage(first.serialize()))
        // Ack session
        aliceCipher.decrypt(SignalMessage(bobCipher.encrypt("ack".toByteArray()).serialize()))

        val aliceStore2 = SignalStoreImpl(context, "signal_alice")
        val bobStore2 = SignalStoreImpl(context, "signal_bob")

        assertTrue(aliceStore2.containsSession(aliceToBob))
        assertTrue(bobStore2.containsSession(bobFromAlice))

        val msg = SessionCipher(aliceStore2, aliceToBob).encrypt("after restart".toByteArray())
        assertEquals(CiphertextMessage.WHISPER_TYPE, msg.type)
        val wire = Base64Compat.encode(msg.serialize())
        val plain = SessionCipher(bobStore2, bobFromAlice)
            .decrypt(SignalMessage(Base64Compat.decode(wire)))
        assertEquals("after restart", String(plain, Charsets.UTF_8))
    }

    @Test
    fun duplicateDelivery_idempotentDecrypt_secondFailsGracefully() {
        val bobBundle = installBobPreKeys()
        val aliceToBob = SignalKeyManager.toAddress(bobUserId, bobDeviceUuid)
        SessionBuilder(aliceStore, aliceToBob).process(bobBundle)

        val aliceCipher = SessionCipher(aliceStore, aliceToBob)
        val bobFromAlice = SignalKeyManager.toAddress(aliceUserId, aliceDeviceUuid)
        val bobCipher = SessionCipher(bobStore, bobFromAlice)

        val encrypted = aliceCipher.encrypt("once".toByteArray())
        val bytes = encrypted.serialize()
        val plain1 = bobCipher.decrypt(PreKeySignalMessage(bytes))
        assertEquals("once", String(plain1, Charsets.UTF_8))

        try {
            bobCipher.decrypt(PreKeySignalMessage(bytes))
            fail("Duplicate decrypt should fail")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun multiDevice_separateSessionsAndCiphertexts() {
        val bobDevice2 = "cccccccc-cccc-cccc-cccc-cccccccccccc"
        val bobStore2 = SignalStoreImpl(context, "signal_bob2")
        bobStore2.clearLocalData()
        assertNotNull(bobStore2.identityKeyPair)

        fun bundleFor(store: SignalStoreImpl, deviceUuid: String): PreKeyBundle {
            val spkId = 1
            val spk = KeyHelper.generateSignedPreKey(store.identityKeyPair, spkId)
            store.storeSignedPreKey(spkId, spk)
            val pks = KeyHelper.generatePreKeys(1, 3)
            pks.forEach { store.storePreKey(it.id, it) }
            val otpk = pks[0]
            return PreKeyBundle(
                store.localRegistrationId,
                SignalKeyManager.signalDeviceId(deviceUuid),
                otpk.id,
                otpk.keyPair.publicKey,
                spkId,
                spk.keyPair.publicKey,
                spk.signature,
                store.identityKeyPair.publicKey
            )
        }

        val b1 = bundleFor(bobStore, bobDeviceUuid)
        val b2 = bundleFor(bobStore2, bobDevice2)

        val addr1 = SignalKeyManager.toAddress(bobUserId, bobDeviceUuid)
        val addr2 = SignalKeyManager.toAddress(bobUserId, bobDevice2)
        SessionBuilder(aliceStore, addr1).process(b1)
        SessionBuilder(aliceStore, addr2).process(b2)

        val c1 = SessionCipher(aliceStore, addr1).encrypt("for-device-1".toByteArray()).serialize()
        val c2 = SessionCipher(aliceStore, addr2).encrypt("for-device-2".toByteArray()).serialize()
        assertFalse(c1.contentEquals(c2))

        val aliceAddr = SignalKeyManager.toAddress(aliceUserId, aliceDeviceUuid)
        val p1 = SessionCipher(bobStore, aliceAddr).decrypt(PreKeySignalMessage(c1))
        val p2 = SessionCipher(bobStore2, aliceAddr).decrypt(PreKeySignalMessage(c2))
        assertEquals("for-device-1", String(p1, Charsets.UTF_8))
        assertEquals("for-device-2", String(p2, Charsets.UTF_8))
    }

    @Test
    fun corruptedCiphertext_failsDecrypt() {
        val bobBundle = installBobPreKeys()
        val aliceToBob = SignalKeyManager.toAddress(bobUserId, bobDeviceUuid)
        SessionBuilder(aliceStore, aliceToBob).process(bobBundle)
        val encrypted = SessionCipher(aliceStore, aliceToBob).encrypt("secret".toByteArray()).serialize()
        encrypted[5] = (encrypted[5].toInt() xor 0xFF).toByte()

        val bobFromAlice = SignalKeyManager.toAddress(aliceUserId, aliceDeviceUuid)
        try {
            SessionCipher(bobStore, bobFromAlice).decrypt(PreKeySignalMessage(encrypted))
            fail("Expected decrypt failure for corrupted ciphertext")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun wrongSession_failsDecrypt() {
        val bobBundle = installBobPreKeys()
        val aliceToBob = SignalKeyManager.toAddress(bobUserId, bobDeviceUuid)
        SessionBuilder(aliceStore, aliceToBob).process(bobBundle)
        val encrypted = SessionCipher(aliceStore, aliceToBob).encrypt("secret".toByteArray()).serialize()

        val charlieStore = SignalStoreImpl(context, "signal_charlie")
        charlieStore.clearLocalData()
        val aliceAddr = SignalKeyManager.toAddress(aliceUserId, aliceDeviceUuid)
        try {
            SessionCipher(charlieStore, aliceAddr).decrypt(PreKeySignalMessage(encrypted))
            fail("Expected decrypt failure for wrong recipient")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun missingSession_whisperFails() {
        val bobFromAlice = SignalKeyManager.toAddress(aliceUserId, aliceDeviceUuid)
        val garbage = ByteArray(48) { 1 }
        try {
            SessionCipher(bobStore, bobFromAlice).decrypt(SignalMessage(garbage))
            fail("Expected failure without session")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun prekeyBundleJson_snakeCaseRoundTrip() {
        val gson = Gson()
        val json = """
            [{
              "device_id":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
              "registration_id":12345,
              "identity_key":"BQabc",
              "signed_prekey_id":1,
              "signed_prekey_public":"BQdef",
              "signed_prekey_signature":"sig==",
              "onetime_prekey_id":7,
              "onetime_prekey_public":"BQghi"
            }]
        """.trimIndent()
        val type = object : com.google.gson.reflect.TypeToken<List<SignalKeyManager.PreKeyBundleDto>>() {}.type
        val list: List<SignalKeyManager.PreKeyBundleDto> = gson.fromJson(json, type)
        assertEquals(1, list.size)
        assertEquals(bobDeviceUuid, list[0].deviceId)
        assertEquals(12345, list[0].registrationId)
        assertEquals(7, list[0].oneTimePreKeyId)
        assertEquals("BQghi", list[0].oneTimePreKeyPublic)
    }

    @Test
    fun isLikelyCiphertext_detectsBase64Blocks() {
        assertTrue(
            SignalKeyManager.isLikelyCiphertext(
                "D0IylT3OyLZ6gPSw2dAHftdeW5gjTB5odBdSP097pr4H0+Jpd/j0jVNI5b/Ht9CLxgmyU="
            )
        )
        assertFalse(SignalKeyManager.isLikelyCiphertext("Hello there"))
        assertFalse(SignalKeyManager.isLikelyCiphertext(SignalKeyManager.DISPLAY_DECRYPT_FAILED))
        assertFalse(SignalKeyManager.isLikelyCiphertext("[Media]"))
    }

    @Test
    fun ciphertextBytes_beforeSend_match_afterReceive() {
        val bobBundle = installBobPreKeys()
        val aliceToBob = SignalKeyManager.toAddress(bobUserId, bobDeviceUuid)
        SessionBuilder(aliceStore, aliceToBob).process(bobBundle)
        val msg = SessionCipher(aliceStore, aliceToBob).encrypt("compare-bytes".toByteArray())
        val before = msg.serialize()
        val transport = Base64Compat.encode(before)
        data class Wire(
            @SerializedName("encrypted_content") val content: String,
            @SerializedName("message_type") val type: Int
        )
        val wireJson = Gson().toJson(Wire(transport, msg.type))
        val parsed = Gson().fromJson(wireJson, Wire::class.java)
        val after = Base64Compat.decode(parsed.content)
        assertArrayEquals(before, after)
        assertEquals(msg.type, parsed.type)
    }

    @Test
    fun signalDeviceId_isPositiveStable() {
        val a = SignalKeyManager.signalDeviceId(bobDeviceUuid)
        val b = SignalKeyManager.signalDeviceId(bobDeviceUuid)
        assertEquals(a, b)
        assertTrue(a > 0)
        assertNotEquals(
            SignalKeyManager.signalDeviceId(aliceDeviceUuid),
            SignalKeyManager.signalDeviceId(bobDeviceUuid)
        )
    }

    @Test
    fun uiNeverShowsRawCiphertext_viaDisplayGuard() {
        val cipherLike = "D0IylT3OyLZ6gPSw2dAHftdeW5gjTB5odBdSP097pr4H0+Jpd/j0jVNI5b/Ht9CLxgmyU="
        val display = if (SignalKeyManager.isLikelyCiphertext(cipherLike)) {
            SignalKeyManager.DISPLAY_DECRYPT_FAILED
        } else cipherLike
        assertEquals(SignalKeyManager.DISPLAY_DECRYPT_FAILED, display)
        assertFalse(display.contains("D0Iyl"))
    }

    @Test
    fun identityKeys_doNotRegenerateAcrossStoreInstances() {
        val first = Base64Compat.encode(aliceStore.identityKeyPair.serialize())
        val reloaded = SignalStoreImpl(context, "signal_alice")
        val second = Base64Compat.encode(reloaded.identityKeyPair.serialize())
        assertEquals(first, second)
    }
}
