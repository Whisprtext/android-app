package com.whisprtext.app.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.whispersystems.libsignal.util.KeyHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class E2EETest {
    @Test
    fun testLocalEncryptor() {
        LocalEncryptor.isEncryptionEnabled = true
        val plaintext = "Secret message text"
        val encrypted = LocalEncryptor.encrypt(plaintext)
        assertNotEquals(plaintext, encrypted)
        val decrypted = LocalEncryptor.decrypt(encrypted)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun testSignalKeyGenerationAndStore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SignalStoreImpl(context)

        val idKeyPair = store.identityKeyPair
        assertNotNull(idKeyPair)

        val registrationId = store.localRegistrationId
        assertTrue(registrationId > 0)

        val preKey = KeyHelper.generatePreKeys(1, 1)[0]
        store.storePreKey(preKey.id, preKey)
        assertTrue(store.containsPreKey(preKey.id))
        assertEquals(preKey.serialize().size, store.loadPreKey(preKey.id).serialize().size)

        store.removePreKey(preKey.id)
        assertFalse(store.containsPreKey(preKey.id))
    }
}
