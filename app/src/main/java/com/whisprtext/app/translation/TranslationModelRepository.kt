package com.whisprtext.app.translation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.gson.Gson
import com.whisprtext.app.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest

sealed class ModelDownloadState {
    object NotDownloaded : ModelDownloadState()
    object Checking : ModelDownloadState()
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val currentFile: String
    ) : ModelDownloadState()
    data class Verifying(val currentFile: String) : ModelDownloadState()
    data class Installed(val version: String, val directory: File) : ModelDownloadState()
    data class Failed(
        val errorMessage: String,
        val isInsufficientStorage: Boolean = false,
        val isWifiRequired: Boolean = false
    ) : ModelDownloadState()
}

data class ManifestFileEntry(
    val filename: String,
    val relative_path: String,
    val size_bytes: Long,
    val sha256: String,
    val url: String? = null
)

data class TranslationManifest(
    val model_name: String,
    val model_version: String,
    val model_dir: String,
    val base_url: String? = null,
    val supported_languages: Map<String, String>,
    val total_files: Int,
    val total_size_bytes: Long,
    val total_size_mb: Float,
    val files: List<ManifestFileEntry>
)

class TranslationModelRepository(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    companion object {
        private const val TAG = "TranslationModelRepo"
        const val MODEL_VERSION = "nllb-200-distilled-600m-int8-1.0.0"
        const val INSTALL_COMPLETE_FLAG = "install.complete"
        const val STORAGE_SAFETY_BUFFER_BYTES = 200 * 1024 * 1024L // 200MB buffer
    }

    private val gson = Gson()
    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Checking)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    val modelDirectory: File
        get() = File(context.filesDir, "translation/$MODEL_VERSION")

    val installCompleteFile: File
        get() = File(modelDirectory, INSTALL_COMPLETE_FLAG)

    suspend fun checkModelInstalledStatus(): Boolean = withContext(Dispatchers.IO) {
        if (!modelDirectory.exists() || !installCompleteFile.exists()) {
            _downloadState.value = ModelDownloadState.NotDownloaded
            return@withContext false
        }
        _downloadState.value = ModelDownloadState.Installed(MODEL_VERSION, modelDirectory)
        true
    }

    suspend fun isModelReady(): Boolean = withContext(Dispatchers.IO) {
        modelDirectory.exists() && installCompleteFile.exists()
    }

    suspend fun downloadAndInstallModel(
        customManifestUrl: String? = null,
        bypassWifiCheck: Boolean = false
    ): Result<File> = withContext(Dispatchers.IO) {
        _downloadState.value = ModelDownloadState.Checking

        // Check network connection & Wi-Fi requirement
        val allowMobileData = bypassWifiCheck || preferencesManager.allowMobileDataDownload.first()
        if (!isNetworkConnected()) {
            val err = "No network connection available"
            logE(TAG, err)
            _downloadState.value = ModelDownloadState.Failed(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        if (!allowMobileData && !isWifiConnected()) {
            val err = "Wi-Fi connection required for model download (~1.8GB)"
            logW(TAG, err)
            _downloadState.value = ModelDownloadState.Failed(err, isWifiRequired = true)
            return@withContext Result.failure(IllegalStateException(err))
        }

        // Fetch manifest
        val manifestUrl = customManifestUrl ?: preferencesManager.manifestUrl.first()
        logI(TAG, "Requesting translation manifest from: $manifestUrl")
        val manifestResult = fetchManifest(manifestUrl)
        if (manifestResult.isFailure) {
            val ex = manifestResult.exceptionOrNull()
            val friendlyErr = formatNetworkErrorMessage("Manifest download failed", manifestUrl, ex)
            logE(TAG, "Manifest fetch error for $manifestUrl: ${ex?.message}", ex)
            _downloadState.value = ModelDownloadState.Failed(friendlyErr)
            return@withContext Result.failure(IllegalStateException(friendlyErr, ex))
        }

        val manifest = manifestResult.getOrThrow()

        // Storage space check
        val freeBytes = context.filesDir.freeSpace
        val requiredBytes = manifest.total_size_bytes + STORAGE_SAFETY_BUFFER_BYTES
        if (freeBytes < requiredBytes) {
            val freeMb = freeBytes / (1024 * 1024)
            val requiredMb = requiredBytes / (1024 * 1024)
            val err = "Insufficient storage space: $freeMb MB available, $requiredMb MB required"
            logE(TAG, err)
            _downloadState.value = ModelDownloadState.Failed(err, isInsufficientStorage = true)
            return@withContext Result.failure(IllegalStateException(err))
        }

        // Prepare temporary download directory
        val tempDir = File(context.filesDir, "translation/temp_$MODEL_VERSION")
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
        tempDir.mkdirs()

        var totalDownloadedBytes = 0L
        val totalExpectedBytes = manifest.total_size_bytes

        try {
            for (fileEntry in manifest.files) {
                val fileUrl = resolveFileUrl(manifestUrl, manifest.base_url, fileEntry)
                val tempFile = File(tempDir, "${fileEntry.filename}.tmp")

                logD(TAG, "Downloading file ${fileEntry.filename} from $fileUrl")
                _downloadState.value = ModelDownloadState.Downloading(
                    progressPercent = if (totalExpectedBytes > 0) ((totalDownloadedBytes.toDouble() / totalExpectedBytes) * 100).toInt() else 0,
                    downloadedBytes = totalDownloadedBytes,
                    totalBytes = totalExpectedBytes,
                    currentFile = fileEntry.filename
                )

                val request = Request.Builder().url(fileUrl).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val httpErr = "Translation model server is unavailable (HTTP ${response.code}). Check development server and URL."
                        logE(TAG, "Failed to download ${fileEntry.filename} from $fileUrl - HTTP ${response.code}")
                        throw IllegalStateException(httpErr)
                    }
                    val body = response.body ?: throw IllegalStateException("Empty response body for ${fileEntry.filename}")

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                totalDownloadedBytes += read
                                val percent = if (totalExpectedBytes > 0) {
                                    ((totalDownloadedBytes.toDouble() / totalExpectedBytes) * 100).coerceIn(0.0, 99.0).toInt()
                                } else 0
                                _downloadState.value = ModelDownloadState.Downloading(
                                    progressPercent = percent,
                                    downloadedBytes = totalDownloadedBytes,
                                    totalBytes = totalExpectedBytes,
                                    currentFile = fileEntry.filename
                                )
                            }
                        }
                    }
                }

                // Verify SHA-256 checksum
                _downloadState.value = ModelDownloadState.Verifying(fileEntry.filename)
                val calculatedHash = computeSha256(tempFile)
                if (!calculatedHash.equals(fileEntry.sha256, ignoreCase = true)) {
                    val checksumErr = "Checksum mismatch for ${fileEntry.filename}: SHA-256 verification failed."
                    logE(TAG, "Checksum mismatch for ${fileEntry.filename}: expected ${fileEntry.sha256}, got $calculatedHash")
                    throw IllegalStateException(checksumErr)
                }

                // Rename from .tmp to final filename in temp dir
                val verifiedFile = File(tempDir, fileEntry.filename)
                if (!tempFile.renameTo(verifiedFile)) {
                    tempFile.copyTo(verifiedFile, overwrite = true)
                    tempFile.delete()
                }
            }

            // Atomic move to final model directory
            if (modelDirectory.exists()) {
                modelDirectory.deleteRecursively()
            }
            modelDirectory.mkdirs()

            for (fileEntry in manifest.files) {
                val tempVerified = File(tempDir, fileEntry.filename)
                val finalTarget = File(modelDirectory, fileEntry.filename)
                if (!tempVerified.renameTo(finalTarget)) {
                    tempVerified.copyTo(finalTarget, overwrite = true)
                    tempVerified.delete()
                }
            }

            // Create install.complete flag
            File(modelDirectory, INSTALL_COMPLETE_FLAG).writeText("installed_at=${System.currentTimeMillis()}\nversion=$MODEL_VERSION")

            tempDir.deleteRecursively()

            _downloadState.value = ModelDownloadState.Installed(MODEL_VERSION, modelDirectory)
            logI(TAG, "Translation model successfully installed to ${modelDirectory.absolutePath}")
            Result.success(modelDirectory)
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            val friendlyMsg = if (e is IllegalStateException && e.message != null && e.message!!.contains("Translation model server")) {
                e.message!!
            } else {
                formatNetworkErrorMessage("Model download failed", manifestUrl, e)
            }
            logE(TAG, "Model download/install failed: ${e.message}", e)
            _downloadState.value = ModelDownloadState.Failed(friendlyMsg)
            Result.failure(e)
        }
    }

    suspend fun deleteModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (modelDirectory.exists()) {
                modelDirectory.deleteRecursively()
            }
            _downloadState.value = ModelDownloadState.NotDownloaded
            true
        } catch (e: Exception) {
            logE(TAG, "Failed to delete model directory", e)
            false
        }
    }

    private fun resolveFileUrl(manifestUrl: String, manifestBaseUrl: String?, entry: ManifestFileEntry): String {
        val relPath = entry.relative_path.removePrefix("/")
        if (relPath.startsWith("http://") || relPath.startsWith("https://")) {
            return relPath
        }
        val manifestBase = manifestUrl.substringBeforeLast('/')
        return "$manifestBase/$relPath"
    }

    private fun fetchManifest(manifestUrl: String): Result<TranslationManifest> {
        return try {
            val request = Request.Builder().url(manifestUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val httpMsg = "Translation model server is unavailable (HTTP ${response.code}). Check development server and URL."
                    logE(TAG, "Manifest request failed: HTTP ${response.code} for URL $manifestUrl")
                    return Result.failure(IllegalStateException(httpMsg))
                }
                val bodyStr = response.body?.string() ?: return Result.failure(IllegalStateException("Empty manifest body"))
                val manifest = gson.fromJson(bodyStr, TranslationManifest::class.java)
                Result.success(manifest)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatNetworkErrorMessage(prefix: String, url: String, e: Throwable?): String {
        return when (e) {
            is UnknownHostException -> "Translation model server is unavailable. Unable to resolve host (${url}). Check DNS or network."
            is ConnectException, is SocketTimeoutException -> "Translation model server is unavailable. Check the development server and URL."
            else -> e?.message ?: "Translation model server is unavailable. Check the development server and URL."
        }
    }

    private fun logE(tag: String, msg: String, tr: Throwable? = null) {
        try { Log.e(tag, msg, tr) } catch (_: Throwable) { println("ERROR: [$tag] $msg") }
    }
    private fun logW(tag: String, msg: String) {
        try { Log.w(tag, msg) } catch (_: Throwable) { println("WARN: [$tag] $msg") }
    }
    private fun logI(tag: String, msg: String) {
        try { Log.i(tag, msg) } catch (_: Throwable) { println("INFO: [$tag] $msg") }
    }
    private fun logD(tag: String, msg: String) {
        try { Log.d(tag, msg) } catch (_: Throwable) { println("DEBUG: [$tag] $msg") }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
