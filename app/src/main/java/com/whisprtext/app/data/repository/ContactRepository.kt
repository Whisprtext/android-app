package com.whisprtext.app.data.repository

import android.content.Context
import com.whisprtext.app.util.ContactHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ContactRepository(private val context: Context) {
    private val _contactsMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val contactsMap: StateFlow<Map<String, String>> = _contactsMap.asStateFlow()

    private var isLoaded = false

    suspend fun loadContacts() {
        if (isLoaded) return
        withContext(Dispatchers.IO) {
            val map = ContactHelper.getContactsMap(context)
            _contactsMap.value = map
            isLoaded = true
        }
    }
    
    fun getCachedContacts(): Map<String, String> = _contactsMap.value
}
