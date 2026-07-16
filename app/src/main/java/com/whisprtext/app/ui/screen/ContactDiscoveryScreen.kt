package com.whisprtext.app.ui.screen

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.whisprtext.app.data.remote.model.UserDto
import com.whisprtext.app.ui.component.InitialsAvatar
import com.whisprtext.app.ui.theme.DynaPuffFontFamily
import com.whisprtext.app.ui.viewmodel.ContactDiscoveryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDiscoveryScreen(
    viewModel: ContactDiscoveryViewModel,
    onBackClick: () -> Unit,
    onChatCreated: (String) -> Unit
) {
    val context = LocalContext.current
    var usernameQuery by remember { mutableStateOf("") }

    val searchResult by viewModel.searchResult.collectAsState()
    val matchedContacts by viewModel.matchedContacts.collectAsState()
    val unmatchedContacts by viewModel.unmatchedContacts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val errorSharedFlow = viewModel.error
    val chatCreatedFlow = viewModel.chatCreated

    LaunchedEffect(Unit) {
        errorSharedFlow.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        chatCreatedFlow.collect { conv ->
            onChatCreated(conv.id)
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.syncContacts(context)
        } else {
            Toast.makeText(context, "Permission to read contacts denied.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Contacts") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Add by Username", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = usernameQuery,
                    onValueChange = { usernameQuery = it },
                    label = { Text("Username") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = { viewModel.searchUser(usernameQuery) },
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }

            searchResult?.let { user ->
                ListItem(
                    modifier = Modifier.fillMaxWidth(),
                    leadingContent = {
                        InitialsAvatar(
                            id = user.username,
                            avatarUrl = user.avatarUrl,
                            modifier = Modifier.size(40.dp)
                        )
                    },
                    headlineContent = { Text(user.username) },
                    supportingContent = { Text("Discoverable user") },
                    trailingContent = {
                        Button(onClick = { viewModel.startChat(user) }) {
                            Text("Chat")
                        }
                    }
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sync Device Contacts", style = MaterialTheme.typography.titleMedium)
                Button(onClick = {
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }) {
                    Text("Sync Now")
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (matchedContacts.isNotEmpty()) {
                        item {
                            Text(
                                "Contacts on WhisprText",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = DynaPuffFontFamily,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        items(matchedContacts) { user ->
                            ListItem(
                                modifier = Modifier.fillMaxWidth(),
                                leadingContent = {
                        InitialsAvatar(
                            id = user.username,
                            avatarUrl = user.avatarUrl,
                            modifier = Modifier.size(40.dp)
                        )
                    },
                                headlineContent = { Text(user.username) },
                                supportingContent = { Text(user.phoneNumber ?: "") },
                                trailingContent = {
                                    Button(onClick = { viewModel.startChat(user) }) {
                                        Text("Chat")
                                    }
                                }
                            )
                        }
                    }

                    if (unmatchedContacts.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Invite to WhisprText",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = DynaPuffFontFamily,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                        items(unmatchedContacts) { contact ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                ListItem(
                                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    leadingContent = {
                                        InitialsAvatar(
                                            id = contact.name,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    },
                                    headlineContent = { Text(contact.name) },
                                    supportingContent = { Text(contact.phoneNumber) },
                                    trailingContent = {
                                        IconButton(onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                    data = Uri.parse("smsto:${contact.phoneNumber}")
                                                    putExtra("sms_body", "Let's chat on WhisprText! It's a secure private messenger.")
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not open SMS client", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Icon(Icons.Default.Share, contentDescription = "Invite")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
