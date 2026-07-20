package com.whisprtext.app.util

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.memory.MemoryCache
import com.whisprtext.app.data.remote.ApiClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves internal avatar references (`r2://avatars/...`) to short-lived download URLs
 * for Coil, with a process-local cache keyed by the stable avatar reference.
 *
 * Unique object keys per avatar revision provide natural cache busting.
 */
object AvatarUrlResolver {
    private val resolved = ConcurrentHashMap<String, String>()

    fun isRemoteAvatarRef(avatarRef: String?): Boolean {
        if (avatarRef.isNullOrBlank()) return false
        return avatarRef.startsWith("r2://") ||
            avatarRef.startsWith("avatars/") ||
            avatarRef.startsWith("http://") ||
            avatarRef.startsWith("https://")
    }

    fun isStorageRef(avatarRef: String?): Boolean {
        if (avatarRef.isNullOrBlank()) return false
        return avatarRef.startsWith("r2://") || avatarRef.startsWith("avatars/")
    }

    suspend fun resolve(apiClient: ApiClient, avatarRef: String?): String? {
        if (avatarRef.isNullOrBlank()) return null
        // Legacy or already-public URLs load directly.
        if (!isStorageRef(avatarRef)) {
            return avatarRef
        }
        resolved[avatarRef]?.let { return it }
        return try {
            val fileUrl = if (avatarRef.startsWith("r2://")) avatarRef else "r2://$avatarRef"
            val downloadUrl = apiClient.getAvatarDownloadUrl(fileUrl).downloadUrl
            resolved[avatarRef] = downloadUrl
            downloadUrl
        } catch (_: Exception) {
            null
        }
    }

    fun invalidate(avatarRef: String?) {
        if (avatarRef.isNullOrBlank()) return
        resolved.remove(avatarRef)
    }

    fun clear() {
        resolved.clear()
    }

    /** Evict Coil memory/disk entries for a previous resolved image URL. */
    @OptIn(ExperimentalCoilApi::class)
    fun evictFromImageLoader(context: Context, imageUrl: String?) {
        if (imageUrl.isNullOrBlank()) return
        val loader = context.imageLoader
        loader.memoryCache?.remove(MemoryCache.Key(imageUrl))
        // diskCache.remove expects the cache key string; best-effort.
        try {
            loader.diskCache?.remove(imageUrl)
        } catch (_: Exception) {
            // ignore
        }
    }
}