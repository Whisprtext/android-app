package com.whisprtext.app.util

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object MediaCrypto {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    fun generateAESKey(): ByteArray {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        return key.encoded
    }

    fun encrypt(plaintext: ByteArray, keyBytes: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val ciphertext = cipher.doFinal(plaintext)
        
        // Return IV + Ciphertext
        val result = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(ciphertext, 0, result, iv.size, ciphertext.size)
        return result
    }

    fun decrypt(encryptedData: ByteArray, keyBytes: ByteArray): ByteArray {
        if (encryptedData.size < IV_LENGTH_BYTE) {
            throw IllegalArgumentException("Invalid encrypted data length")
        }
        val secretKey = SecretKeySpec(keyBytes, "AES")
        
        val iv = ByteArray(IV_LENGTH_BYTE)
        System.arraycopy(encryptedData, 0, iv, 0, IV_LENGTH_BYTE)

        val ciphertextLength = encryptedData.size - IV_LENGTH_BYTE
        val ciphertext = ByteArray(ciphertextLength)
        System.arraycopy(encryptedData, IV_LENGTH_BYTE, ciphertext, 0, ciphertextLength)

        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return cipher.doFinal(ciphertext)
    }

    fun sha256(data: ByteArray): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }
}
