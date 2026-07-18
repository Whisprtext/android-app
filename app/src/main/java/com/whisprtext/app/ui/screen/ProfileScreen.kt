package com.whisprtext.app.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import com.whisprtext.app.ui.theme.Motion
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisprtext.app.ui.component.AvatarEditorDialog
import com.whisprtext.app.ui.component.AvatarManagementSheet
import com.whisprtext.app.ui.component.UserAvatar
import com.whisprtext.app.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onAppearanceClick: () -> Unit = {},
    isOwnProfile: Boolean = true
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val gradientColors by viewModel.gradientColors.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isAvatarUploading by viewModel.isAvatarUploading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
    )

    // Profile fields states
    var usernameInput by remember { mutableStateOf("") }
    var displayNameInput by remember { mutableStateOf("") }
    var bioInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var currentAvatarUrl by remember { mutableStateOf<String?>(null) }

    var showAvatarSheet by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var editorImageUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            editorImageUri = uri
        }
        // Cancellation is a no-op; sheet already closed when picker opened.
    }

    // Sync input states when userProfile updates
    LaunchedEffect(userProfile) {
        userProfile?.let { user ->
            usernameInput = user.username
            displayNameInput = user.displayName
            bioInput = user.bio
            currentAvatarUrl = user.avatarUrl.takeIf { it.isNotBlank() }
            phoneInput = user.phoneNumber ?: ""
        }
    }

    LaunchedEffect(Unit) {
        viewModel.successMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val isDark = isSystemInDarkTheme()
    val hasCustomAvatar = !currentAvatarUrl.isNullOrBlank()

    if (showAvatarSheet && isOwnProfile) {
        ModalBottomSheet(
            onDismissRequest = { if (!isAvatarUploading) showAvatarSheet = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            AvatarManagementSheet(
                hasCustomAvatar = hasCustomAvatar,
                isBusy = isAvatarUploading,
                onUploadClick = {
                    showAvatarSheet = false
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemoveClick = {
                    showAvatarSheet = false
                    showRemoveConfirm = true
                },
                onDismiss = { showAvatarSheet = false }
            )
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isAvatarUploading) showRemoveConfirm = false },
            title = { Text("Remove photo?") },
            text = { Text("Your profile will show your initials instead.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveConfirm = false
                        viewModel.removeAvatar()
                    },
                    enabled = !isAvatarUploading
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRemoveConfirm = false },
                    enabled = !isAvatarUploading
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    editorImageUri?.let { uri ->
        AvatarEditorDialog(
            imageUri = uri,
            isUploading = isAvatarUploading,
            onDismiss = {
                if (!isAvatarUploading) editorImageUri = null
            },
            onConfirm = { bytes ->
                viewModel.uploadAvatar(bytes) { success ->
                    if (success) {
                        editorImageUri = null
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
                ) {
                    TopAppBar(
                        title = { Text("Profile") },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (isOwnProfile) {
                                IconButton(onClick = onSettingsClick) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
                if (isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }
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
        } else if (!isOwnProfile) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val avatarLabel = displayNameInput.ifBlank { usernameInput }
                    UserAvatar(
                        id = avatarLabel,
                        modifier = Modifier.size(200.dp),
                        avatarUrl = currentAvatarUrl,
                        fontSize = 80.sp,
                        gradientStart = gradientColors.first?.let { Color(it) },
                        gradientEnd = gradientColors.second?.let { Color(it) }
                    )
                }

                val displayBio = bioInput.ifBlank { "No bio provided" }
                Text(
                    text = "\"$displayBio\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )

                if (phoneInput.isNotBlank()) {
                    Text(
                        text = phoneInput,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                // Tappable profile avatar
                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isAvatarUploading) { showAvatarSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    val avatarLabel = displayNameInput.ifBlank { usernameInput }
                    UserAvatar(
                        id = avatarLabel,
                        modifier = Modifier.size(180.dp),
                        avatarUrl = currentAvatarUrl,
                        fontSize = 72.sp,
                        gradientStart = gradientColors.first?.let { Color(it) },
                        gradientEnd = gradientColors.second?.let { Color(it) },
                        contentDescription = "Profile photo. Tap to change."
                    )
                    // Camera badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(44.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isAvatarUploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    Icons.Default.PhotoCamera,
                                    contentDescription = "Change photo",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Tap to change profile photo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Profile Info", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider()

                    val isDisplayNameValid = viewModel.validateDisplayName(displayNameInput)
                    OutlinedTextField(
                        value = displayNameInput,
                        onValueChange = { displayNameInput = it },
                        label = { Text("Display Name") },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                        isError = !isDisplayNameValid && displayNameInput.isNotEmpty(),
                        supportingText = {
                            if (!isDisplayNameValid && displayNameInput.isNotEmpty()) {
                                Text("Only alphabets and spaces are allowed", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = fieldColors
                    )

                    val isUsernameValid = viewModel.validateUsername(usernameInput)
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                        isError = !isUsernameValid && usernameInput.isNotEmpty(),
                        supportingText = {
                            if (!isUsernameValid && usernameInput.isNotEmpty()) {
                                Text("Alphanumeric, dot (.), and underscores (_) only", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = fieldColors
                    )

                    OutlinedTextField(
                        value = bioInput,
                        onValueChange = { bioInput = it },
                        label = { Text("Bio / Status message") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        colors = fieldColors
                    )

                    Button(
                        onClick = {
                            viewModel.saveProfile(
                                username = usernameInput,
                                displayName = displayNameInput,
                                bio = bioInput
                            )
                        },
                        enabled = !isLoading && !isAvatarUploading && isUsernameValid && isDisplayNameValid,
                        modifier = Modifier.align(Alignment.End),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Profile")
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Privacy & Discovery", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPrivacyClick() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Who can see my phone number", style = MaterialTheme.typography.bodyLarge)
                                Text("Manage discoverability and visibility", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAppearanceClick() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Appearance", style = MaterialTheme.typography.bodyLarge)
                                Text("App theme, colors and doodles", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}
