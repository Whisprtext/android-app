package com.whisprtext.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisprtext.app.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    // Profile fields states
    var usernameInput by remember { mutableStateOf("") }
    var displayNameInput by remember { mutableStateOf("") }
    var bioInput by remember { mutableStateOf("") }
    var avatarInput by remember { mutableStateOf("") }

    // Privacy fields states
    var phoneInput by remember { mutableStateOf("") }
    var discUsernameInput by remember { mutableStateOf(true) }
    var discPhoneInput by remember { mutableStateOf(true) }
    var phoneVisibilityInput by remember { mutableStateOf("everyone") }

    // Sync input states when userProfile updates
    LaunchedEffect(userProfile) {
        userProfile?.let { user ->
            usernameInput = user.username
            displayNameInput = user.displayName
            bioInput = user.bio
            avatarInput = user.avatarUrl
            phoneInput = user.phoneNumber ?: ""
            discUsernameInput = user.discoverableByUsername
            discPhoneInput = user.discoverableByPhone
            phoneVisibilityInput = user.phoneNumberVisibility
        }
    }

    LaunchedEffect(Unit) {
        viewModel.successMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (isLoading && userProfile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Avatar Indicator
                Box(
                    modifier = Modifier.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val avatarLabel = if (displayNameInput.isNotBlank()) displayNameInput else usernameInput
                    InitialsAvatar(
                        id = if (avatarLabel.isNotBlank()) avatarLabel else "?",
                        modifier = Modifier.size(80.dp)
                    )
                }

                // Error alert box
                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 14.sp
                        )
                    }
                }

                // Card 1: User Identity Settings
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Profile Info", style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider()

                        OutlinedTextField(
                            value = displayNameInput,
                            onValueChange = { displayNameInput = it },
                            label = { Text("Display Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        val isUsernameValid = viewModel.validateUsername(usernameInput)
                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            label = { Text("Username") },
                            isError = !isUsernameValid && usernameInput.isNotEmpty(),
                            supportingText = {
                                if (!isUsernameValid && usernameInput.isNotEmpty()) {
                                    Text("Alphanumeric, dot (.), and underscores (_) only", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = bioInput,
                            onValueChange = { bioInput = it },
                            label = { Text("Bio / Status message") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 3
                        )

                        OutlinedTextField(
                            value = avatarInput,
                            onValueChange = { avatarInput = it },
                            label = { Text("Avatar Photo URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                viewModel.saveProfile(
                                    username = usernameInput,
                                    displayName = displayNameInput,
                                    bio = bioInput,
                                    avatarUrl = avatarInput
                                )
                            },
                            enabled = !isLoading && isUsernameValid,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Save Profile")
                        }
                    }
                }

                // Card 2: Discovery & Privacy
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Privacy & Discovery", style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider()

                        val isPhoneValid = viewModel.validatePhone(phoneInput)
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text("Phone Number") },
                            isError = !isPhoneValid && phoneInput.isNotEmpty(),
                            supportingText = {
                                if (!isPhoneValid && phoneInput.isNotEmpty()) {
                                    Text("International format required (e.g. +15555551234)", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Discoverable by Username", fontSize = 15.sp)
                                Text(
                                    "Allow searches by username",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = discUsernameInput,
                                onCheckedChange = { discUsernameInput = it }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Discoverable by Phone", fontSize = 15.sp)
                                Text(
                                    "Allow matching from other contacts list",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = discPhoneInput,
                                onCheckedChange = { discPhoneInput = it }
                            )
                        }

                        // Phone Number Visibility Select List
                        var expanded by remember { mutableStateOf(false) }
                        val visibilityOptions = listOf("everyone", "contacts", "hidden")
                        val visibilityLabels = mapOf(
                            "everyone" to "Everyone",
                            "contacts" to "Contacts Only",
                            "hidden" to "Nobody (Hidden)"
                        )

                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = visibilityLabels[phoneVisibilityInput] ?: phoneVisibilityInput,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Who can see my phone number") },
                                trailingIcon = {
                                    IconButton(onClick = { expanded = !expanded }) {
                                        Text("▼", fontSize = 10.sp)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = !expanded }
                            )
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                visibilityOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(visibilityLabels[option] ?: option) },
                                        onClick = {
                                            phoneVisibilityInput = option
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.savePrivacySettings(
                                    phoneNumber = phoneInput,
                                    discoverableByUsername = discUsernameInput,
                                    discoverableByPhone = discPhoneInput,
                                    phoneNumberVisibility = phoneVisibilityInput
                                )
                            },
                            enabled = !isLoading && isPhoneValid,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Save Privacy")
                        }
                    }
                }
            }
        }
    }
}
