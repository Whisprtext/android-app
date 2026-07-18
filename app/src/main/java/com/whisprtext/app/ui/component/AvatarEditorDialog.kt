package com.whisprtext.app.ui.component

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.whisprtext.app.util.AvatarFilter
import com.whisprtext.app.util.AvatarImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarEditorDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (ByteArray) -> Unit,
    isUploading: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedFilter by remember { mutableStateOf(AvatarFilter.ORIGINAL) }

    LaunchedEffect(imageUri) {
        loadError = null
        sourceBitmap = null
        try {
            sourceBitmap = withContext(Dispatchers.IO) {
                AvatarImageProcessor.decodeSampled(context, imageUri)
            }
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        } catch (e: Exception) {
            loadError = e.localizedMessage ?: "Unsupported or corrupt image"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            sourceBitmap?.recycle()
            sourceBitmap = null
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isUploading && !isExporting) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isUploading && !isExporting,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(0.dp)
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Edit photo") },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                enabled = !isUploading && !isExporting
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel")
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    val bmp = sourceBitmap ?: return@TextButton
                                    if (viewportSize.width <= 0 || isExporting || isUploading) return@TextButton
                                    scope.launch {
                                        isExporting = true
                                        try {
                                            val bytes = withContext(Dispatchers.Default) {
                                                AvatarImageProcessor.exportSquareJpeg(
                                                    source = bmp,
                                                    scale = scale,
                                                    offsetX = offsetX,
                                                    offsetY = offsetY,
                                                    viewportSize = viewportSize.width.toFloat(),
                                                    filter = selectedFilter
                                                )
                                            }
                                            onConfirm(bytes)
                                        } catch (e: Exception) {
                                            loadError = e.localizedMessage ?: "Failed to export image"
                                        } finally {
                                            isExporting = false
                                        }
                                    }
                                },
                                enabled = sourceBitmap != null && !isUploading && !isExporting
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Done")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        loadError != null && sourceBitmap == null -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = loadError ?: "Failed to load image",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = onDismiss) { Text("Close") }
                                }
                            }
                        }
                        sourceBitmap == null -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        else -> {
                            val bitmap = sourceBitmap!!
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .onSizeChanged { viewportSize = it }
                                        .pointerInput(Unit) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                                scale = newScale
                                                // Allow pan only when zoomed; clamp roughly to viewport.
                                                val maxPan = (viewportSize.width * (newScale - 1f)) / 2f + 40f
                                                offsetX = (offsetX + pan.x).coerceIn(-maxPan, maxPan)
                                                offsetY = (offsetY + pan.y).coerceIn(-maxPan, maxPan)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val colorFilter = remember(selectedFilter) {
                                        filterColorFilter(selectedFilter)
                                    }
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Crop preview",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        colorFilter = colorFilter,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                                translationX = offsetX
                                                translationY = offsetY
                                            }
                                    )
                                    // Soft circular guide matching WhisprText avatars
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val r = size.minDimension / 2f
                                        drawCircle(
                                            color = Color.White.copy(alpha = 0.35f),
                                            radius = r - 2f,
                                            center = Offset(size.width / 2f, size.height / 2f),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Pinch to zoom · drag to reposition",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(AvatarFilter.entries) { filter ->
                                    FilterChip(
                                        selected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter },
                                        label = {
                                            Text(
                                                when (filter) {
                                                    AvatarFilter.ORIGINAL -> "Original"
                                                    AvatarFilter.GRAYSCALE -> "Grayscale"
                                                    AvatarFilter.WARM -> "Warm"
                                                    AvatarFilter.COOL -> "Cool"
                                                }
                                            )
                                        },
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                            }

                            if (loadError != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = loadError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    if (isUploading || isExporting) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (isUploading) "Uploading…" else "Preparing…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarManagementSheet(
    hasCustomAvatar: Boolean,
    onUploadClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onDismiss: () -> Unit,
    isBusy: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Profile photo",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))

            SheetActionRow(
                label = if (hasCustomAvatar) "Change photo" else "Upload photo",
                enabled = !isBusy,
                onClick = onUploadClick
            )
            if (hasCustomAvatar) {
                SheetActionRow(
                    label = "Remove photo",
                    enabled = !isBusy,
                    destructive = true,
                    onClick = onRemoveClick
                )
            }
            SheetActionRow(
                label = "Cancel",
                enabled = !isBusy,
                onClick = onDismiss
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetActionRow(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            destructive -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        }
    )
}

private fun filterColorFilter(filter: AvatarFilter): ColorFilter? {
    return when (filter) {
        AvatarFilter.ORIGINAL -> null
        AvatarFilter.GRAYSCALE -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        AvatarFilter.WARM -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    1.15f, 0f, 0f, 0f, 12f,
                    0f, 1.05f, 0f, 0f, 6f,
                    0f, 0f, 0.9f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        AvatarFilter.COOL -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.9f, 0f, 0f, 0f, 0f,
                    0f, 1.0f, 0f, 0f, 4f,
                    0f, 0f, 1.15f, 0f, 12f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
}
