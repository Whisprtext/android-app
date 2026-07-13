package com.whisprtext.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object ContactHelper {
    fun normalizePhone(phone: String): String? {
        val trimmed = phone.trim()
        if (!trimmed.startsWith("+")) {
            val digits = trimmed.filter { it.isDigit() }
            if (digits.length >= 7) {
                return "+$digits"
            }
            return null
        }
        val digits = trimmed.substring(1).filter { it.isDigit() }
        if (digits.length >= 7) {
            return "+$digits"
        }
        return null
    }

    fun getContactsMap(context: Context): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) 
            != PackageManager.PERMISSION_GRANTED) {
            return map
        }
        
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (nameIndex != -1 && numberIndex != -1) {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIndex) ?: ""
                        val number = cursor.getString(numberIndex) ?: ""
                        if (number.isNotBlank()) {
                            val normalized = normalizePhone(number)
                            if (normalized != null) {
                                map[normalized] = name
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }
}
