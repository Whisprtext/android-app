package com.whisprtext.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisprtext.app.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    viewModel: ProfileViewModel,
    onBackClick: () -> Unit
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary,
        errorContainerColor = Color.Transparent,
    )

    var phoneInput by remember { mutableStateOf("") }
    var discUsernameInput by remember { mutableStateOf(true) }
    var discPhoneInput by remember { mutableStateOf(true) }
    var phoneVisibilityInput by remember { mutableStateOf("everyone") }

    LaunchedEffect(userProfile) {
        userProfile?.let { user ->
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
                title = { Text("Privacy") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading && userProfile == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
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

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val isPhoneValid = viewModel.validatePhone(phoneInput)
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Phone Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        isError = !isPhoneValid && phoneInput.isNotEmpty(),
                        supportingText = {
                            if (!isPhoneValid && phoneInput.isNotEmpty()) {
                                Text("International format required (e.g. +15555551234)", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = fieldColors
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PersonSearch, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Discoverable by Username", fontSize = 15.sp)
                                Text(
                                    "Allow searches by username",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContactPhone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Discoverable by Phone", fontSize = 15.sp)
                                Text(
                                    "Allow matching from other contacts list",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = discPhoneInput,
                            onCheckedChange = { discPhoneInput = it }
                        )
                    }

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
                            leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { expanded = !expanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded },
                            colors = fieldColors
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
