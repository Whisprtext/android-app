package com.whisprtext.app.util

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.whisprtext.app.R
import com.whisprtext.app.data.local.AppDatabase
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object StartupInitializer {
    fun initialize(context: Context, database: AppDatabase, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            // 1. Warm up fonts
            preloadFonts(context)
            
            // 2. Warm up database
            warmupDatabase(database)
            
            // 3. Preload common image assets
            preloadImages(context)
        }
    }
    
    private fun preloadImages(context: Context) {
        val loader = context.imageLoader
        val imageResources = listOf(
            R.mipmap.ic_launcher_foreground,
            // Add other common icons or assets here
        )
        
        imageResources.forEach { resId ->
            val request = ImageRequest.Builder(context)
                .data(resId)
                .build()
            loader.enqueue(request)
        }
    }

    private fun preloadFonts(context: Context) {
        val fontIds = listOf(
            R.font.poppins_regular,
            R.font.poppins_medium,
            R.font.poppins_semibold,
            R.font.inter_regular,
            R.font.inter_medium,
            R.font.dynapuff_medium,
            R.font.dynapuff_semibold
        )
        fontIds.forEach { id ->
            try {
                ResourcesCompat.getFont(context, id)
            } catch (e: Exception) {
                // Ignore preloading errors
            }
        }
    }

    private fun warmupDatabase(database: AppDatabase) {
        try {
            // Just trigger the database opening
            database.openHelper.readableDatabase
        } catch (e: Exception) {
            // Ignore warmup errors
        }
    }
}
