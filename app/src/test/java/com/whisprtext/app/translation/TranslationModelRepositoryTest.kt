package com.whisprtext.app.translation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.whisprtext.app.BuildConfig
import com.whisprtext.app.data.local.PreferencesManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.net.ConnectException
import java.net.UnknownHostException
import java.security.MessageDigest

class TranslationModelRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockPreferencesManager: PreferencesManager
    private lateinit var mockConnectivityManager: ConnectivityManager
    private lateinit var filesDir: File

    @Before
    fun setup() {
        filesDir = tempFolder.newFolder("files")
        
        val mockNetwork = mock<android.net.Network>()
        val mockCaps = mock<NetworkCapabilities> {
            on { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } doReturn true
            on { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } doReturn true
        }

        mockConnectivityManager = mock {
            on { activeNetwork } doReturn mockNetwork
            on { getNetworkCapabilities(mockNetwork) } doReturn mockCaps
        }

        mockContext = mock {
            on { filesDir } doReturn filesDir
            on { getSystemService(Context.CONNECTIVITY_SERVICE) } doReturn mockConnectivityManager
        }

        mockPreferencesManager = mock {
            on { allowMobileDataDownload } doReturn flowOf(true)
            on { manifestUrl } doReturn flowOf("http://10.0.2.2:8000/translation-manifest.json")
        }
    }

    @Test
    fun testNotInstalledInitially() = runTest {
        val repository = TranslationModelRepository(mockContext, mockPreferencesManager)
        val installed = repository.checkModelInstalledStatus()
        assertFalse(installed)
        assertEquals(ModelDownloadState.NotDownloaded, repository.downloadState.value)
    }

    @Test
    fun testDetectsExistingValidInstallation() = runTest {
        val repository = TranslationModelRepository(mockContext, mockPreferencesManager)
        val modelDir = repository.modelDirectory
        modelDir.mkdirs()
        File(modelDir, TranslationModelRepository.INSTALL_COMPLETE_FLAG).writeText("installed")

        val installed = repository.checkModelInstalledStatus()
        assertTrue(installed)
        assertTrue(repository.downloadState.value is ModelDownloadState.Installed)
    }

    @Test
    fun testDeleteModelRemovesDirectory() = runTest {
        val repository = TranslationModelRepository(mockContext, mockPreferencesManager)
        val modelDir = repository.modelDirectory
        modelDir.mkdirs()
        File(modelDir, TranslationModelRepository.INSTALL_COMPLETE_FLAG).writeText("installed")

        val deleted = repository.deleteModel()
        assertTrue(deleted)
        assertFalse(modelDir.exists())
        assertEquals(ModelDownloadState.NotDownloaded, repository.downloadState.value)
    }

    @Test
    fun testDebugManifestUrlSelection() = runTest {
        assertEquals("http://10.0.2.2:8000/translation-manifest.json", BuildConfig.TRANSLATION_MANIFEST_URL)
    }

    @Test
    fun testEmulatorUrlConfiguration() = runTest {
        val repository = TranslationModelRepository(mockContext, mockPreferencesManager)
        val testUrl = "http://10.0.2.2:8000/translation-manifest.json"
        
        // Simulating failure to connect to emulator URL when server is offline
        val client = OkHttpClient.Builder().addInterceptor {
            throw ConnectException("Failed to connect to /10.0.2.2:8000")
        }.build()

        val repoWithClient = TranslationModelRepository(mockContext, mockPreferencesManager, client)
        val result = repoWithClient.downloadAndInstallModel(customManifestUrl = testUrl)

        assertTrue(result.isFailure)
        val state = repoWithClient.downloadState.value
        assertTrue(state is ModelDownloadState.Failed)
        val failedState = state as ModelDownloadState.Failed
        assertTrue(failedState.errorMessage.contains("Translation model server is unavailable"))
    }

    @Test
    fun testInvalidHostnameError() = runTest {
        val client = OkHttpClient.Builder().addInterceptor {
            throw UnknownHostException("cdn.invalid-placeholder.app")
        }.build()

        val repository = TranslationModelRepository(mockContext, mockPreferencesManager, client)
        val result = repository.downloadAndInstallModel("http://cdn.invalid-placeholder.app/manifest.json")

        assertTrue(result.isFailure)
        val state = repository.downloadState.value
        assertTrue(state is ModelDownloadState.Failed)
        val failedState = state as ModelDownloadState.Failed
        assertTrue(failedState.errorMessage.contains("Unable to resolve host"))
    }

    @Test
    fun testManifestHttpError() = runTest {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .body("Manifest not found".toResponseBody("text/plain".toMediaType()))
                .build()
        }.build()

        val repository = TranslationModelRepository(mockContext, mockPreferencesManager, client)
        val result = repository.downloadAndInstallModel("http://10.0.2.2:8000/translation-manifest.json")

        assertTrue(result.isFailure)
        val state = repository.downloadState.value
        assertTrue(state is ModelDownloadState.Failed)
        val failedState = state as ModelDownloadState.Failed
        assertTrue(failedState.errorMessage.contains("HTTP 404"))
    }

    @Test
    fun testSuccessfulLocalManifestDownloadAndChecksumValidation() = runTest {
        val dummyContent = "dummy text content for testing"
        val sha256 = MessageDigest.getInstance("SHA-256").digest(dummyContent.toByteArray()).joinToString("") { "%02x".format(it) }

        val manifestJson = """
            {
              "model_name": "nllb-200-distilled-600M",
              "model_version": "${TranslationModelRepository.MODEL_VERSION}",
              "model_dir": "models/nllb-int8",
              "base_url": "http://10.0.2.2:8000",
              "supported_languages": {"English": "eng_Latn"},
              "total_files": 1,
              "total_size_bytes": ${dummyContent.length},
              "total_size_mb": 0.01,
              "files": [
                {
                  "filename": "config.json",
                  "relative_path": "models/nllb-int8/config.json",
                  "size_bytes": ${dummyContent.length},
                  "sha256": "$sha256",
                  "url": "http://10.0.2.2:8000/models/nllb-int8/config.json"
                }
              ]
            }
        """.trimIndent()

        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            val body = if (url.contains("manifest.json")) {
                manifestJson.toResponseBody("application/json".toMediaType())
            } else {
                dummyContent.toResponseBody("application/octet-stream".toMediaType())
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build()
        }.build()

        val repository = TranslationModelRepository(mockContext, mockPreferencesManager, client)
        val result = repository.downloadAndInstallModel("http://10.0.2.2:8000/translation-manifest.json")

        assertTrue(result.isSuccess)
        assertTrue(repository.isModelReady())
        val state = repository.downloadState.value
        assertTrue(state is ModelDownloadState.Installed)
    }

    @Test
    fun testChecksumMismatchCleanup() = runTest {
        val dummyContent = "dummy text content for testing"
        val badSha256 = "0000000000000000000000000000000000000000000000000000000000000000"

        val manifestJson = """
            {
              "model_name": "nllb-200-distilled-600M",
              "model_version": "${TranslationModelRepository.MODEL_VERSION}",
              "model_dir": "models/nllb-int8",
              "supported_languages": {"English": "eng_Latn"},
              "total_files": 1,
              "total_size_bytes": ${dummyContent.length},
              "total_size_mb": 0.01,
              "files": [
                {
                  "filename": "config.json",
                  "relative_path": "models/nllb-int8/config.json",
                  "size_bytes": ${dummyContent.length},
                  "sha256": "$badSha256"
                }
              ]
            }
        """.trimIndent()

        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            val body = if (url.contains("manifest.json")) {
                manifestJson.toResponseBody("application/json".toMediaType())
            } else {
                dummyContent.toResponseBody("application/octet-stream".toMediaType())
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build()
        }.build()

        val repository = TranslationModelRepository(mockContext, mockPreferencesManager, client)
        val result = repository.downloadAndInstallModel("http://10.0.2.2:8000/translation-manifest.json")

        assertTrue(result.isFailure)
        assertFalse(repository.isModelReady())
        val tempDir = File(filesDir, "translation/temp_${TranslationModelRepository.MODEL_VERSION}")
        assertFalse("Temp directory should be cleaned up on failure", tempDir.exists())
    }
}
