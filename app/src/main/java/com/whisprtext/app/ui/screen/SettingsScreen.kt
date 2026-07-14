package com.whisprtext.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisprtext.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit,
    onLogoutSuccess: () -> Unit
) {
    val username by viewModel.username.collectAsState()
    val userId by viewModel.userId.collectAsState()

    val currentPhone by viewModel.phoneNumber.collectAsState()
    val discUser by viewModel.discoverableByUsername.collectAsState()
    val discPhone by viewModel.discoverableByPhone.collectAsState()

    var phoneInput by remember(currentPhone) { mutableStateOf(currentPhone ?: "") }
    var discUserInput by remember(discUser) { mutableStateOf(discUser) }
    var discPhoneInput by remember(discPhone) { mutableStateOf(discPhone) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.updateStatus.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onProfileClick() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Profile & Privacy Settings", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Edit name, username, bio, avatar, and visibility settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("User Profile Summary", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    Text("Username: $username", fontSize = 16.sp)
                    Text("User ID: $userId", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Privacy & Discovery", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Phone Number (e.g. +15555551234)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Discoverable by Username", fontSize = 16.sp)
                            Text(
                                "Allows others to search you by handle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = discUserInput,
                            onCheckedChange = { discUserInput = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Discoverable by Phone", fontSize = 16.sp)
                            Text(
                                "Allows matching from address books",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = discPhoneInput,
                            onCheckedChange = { discPhoneInput = it }
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.saveSettings(phoneInput, discUserInput, discPhoneInput)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Settings")
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.logout(onLogoutSuccess)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Log Out", color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}
