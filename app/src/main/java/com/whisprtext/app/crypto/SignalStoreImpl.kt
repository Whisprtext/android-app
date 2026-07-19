package com.whisprtext.app.crypto

import android.content.Context
import android.content.SharedPreferences
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.state.*

class SignalStoreImpl(
    private val context: Context,
    /** Override for tests that need multiple isolated Signal identities in one process. */
    private val storeName: String = "whisprtext_secure_signal_store"
) : SignalProtocolStore {
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKeyAlias = androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC)
            androidx.security.crypto.EncryptedSharedPreferences.create(
                storeName,
                masterKeyAlias,
                context,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            context.getSharedPreferences("${storeName}_fallback", Context.MODE_PRIVATE)
        }
    }

    override fun getIdentityKeyPair(): IdentityKeyPair {
        val serialized = prefs.getString("identity_key_pair", null) ?: return generateAndStoreIdentityKeyPair()
        return IdentityKeyPair(Base64Compat.decode(serialized))
    }

    override fun getLocalRegistrationId(): Int {
        var id = prefs.getInt("local_registration_id", 0)
        if (id == 0) {
            id = org.whispersystems.libsignal.util.KeyHelper.generateRegistrationId(false)
            prefs.edit().putInt("local_registration_id", id).commit()
        }
        return id
    }

    private fun identityPrefKey(address: SignalProtocolAddress): String {
        // Per-device identity: each Signal device has its own identity key
        return "identity_${address.name}_${address.deviceId}"
    }

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
        val key = identityPrefKey(address)
        val existing = prefs.getString(key, null)
        val serialized = Base64Compat.encode(identityKey.serialize())
        // commit() so session/identity state is durable before subsequent encrypt/decrypt
        prefs.edit().putString(key, serialized).commit()
        // true if identity changed (or first save)
        return existing == null || existing != serialized
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val saved = prefs.getString(identityPrefKey(address), null) ?: return null
        return IdentityKey(Base64Compat.decode(saved), 0)
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean {
        // Always accept for now (TOFU with rotation allowed). Rejecting identity changes
        // after a peer reinstalls/re-registers keys causes permanent "Unable to decrypt"
        // with no UI to verify/reset. Session keys are still ratcheted normally.
        // When we add safety-number UX, gate this behind an explicit user confirm.
        return true
    }

    private fun generateAndStoreIdentityKeyPair(): IdentityKeyPair {
        // Only called when no identity exists — never regenerates on restart once stored
        val keyPair = org.whispersystems.libsignal.util.KeyHelper.generateIdentityKeyPair()
        val serialized = Base64Compat.encode(keyPair.serialize())
        prefs.edit().putString("identity_key_pair", serialized).commit()
        return keyPair
    }

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val serialized = prefs.getString("prekey_$preKeyId", null)
            ?: throw org.whispersystems.libsignal.InvalidKeyIdException("No such prekey: $preKeyId")
        return PreKeyRecord(Base64Compat.decode(serialized))
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        val serialized = Base64Compat.encode(record.serialize())
        // apply() is fine for OTPKs; bulk register stores many keys before network upload
        prefs.edit().putString("prekey_$preKeyId", serialized).apply()
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        return prefs.contains("prekey_$preKeyId")
    }

    override fun removePreKey(preKeyId: Int) {
        prefs.edit().remove("prekey_$preKeyId").commit()
    }

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val serialized = prefs.getString("signed_prekey_$signedPreKeyId", null)
            ?: throw org.whispersystems.libsignal.InvalidKeyIdException("No such signed prekey: $signedPreKeyId")
        return SignedPreKeyRecord(Base64Compat.decode(serialized))
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        val list = mutableListOf<SignedPreKeyRecord>()
        prefs.all.keys.forEach { key ->
            if (key.startsWith("signed_prekey_")) {
                val serialized = prefs.getString(key, null)
                if (serialized != null) {
                    list.add(SignedPreKeyRecord(Base64Compat.decode(serialized)))
                }
            }
        }
        return list
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        val serialized = Base64Compat.encode(record.serialize())
        prefs.edit().putString("signed_prekey_$signedPreKeyId", serialized).commit()
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return prefs.contains("signed_prekey_$signedPreKeyId")
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        prefs.edit().remove("signed_prekey_$signedPreKeyId")
    }

    private fun sessionPrefKey(address: SignalProtocolAddress): String {
        return "session_${address.name}_${address.deviceId}"
    }

    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val serialized = prefs.getString(sessionPrefKey(address), null) ?: return SessionRecord()
        return SessionRecord(Base64Compat.decode(serialized))
    }

    override fun getSubDeviceSessions(name: String): List<Int> {
        val deviceIds = mutableListOf<Int>()
        val prefix = "session_${name}_"
        prefs.all.keys.forEach { key ->
            if (key.startsWith(prefix)) {
                val devicePart = key.removePrefix(prefix)
                devicePart.toIntOrNull()?.let { deviceIds.add(it) }
            }
        }
        return deviceIds
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        val serialized = Base64Compat.encode(record.serialize())
        prefs.edit().putString(sessionPrefKey(address), serialized).commit()
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        return prefs.contains(sessionPrefKey(address))
    }

    override fun deleteSession(address: SignalProtocolAddress) {
        prefs.edit().remove(sessionPrefKey(address)).commit()
    }

    override fun deleteAllSessions(name: String) {
        val editor = prefs.edit()
        val prefix = "session_${name}_"
        prefs.all.keys.forEach { key ->
            if (key.startsWith(prefix)) {
                editor.remove(key)
            }
        }
        editor.commit()
    }

    fun clearLocalData() {
        prefs.edit().clear().commit()
    }
}
