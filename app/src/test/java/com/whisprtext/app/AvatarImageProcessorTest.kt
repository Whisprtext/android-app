package com.whisprtext.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.whisprtext.app.util.AvatarFilter
import com.whisprtext.app.util.AvatarImageProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AvatarImageProcessorTest {

    @Test
    fun decodeSampledOpensJpegWithoutFalseUnableToOpen() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        source.eraseColor(0xFF00AA55.toInt())
        val file = File(context.cacheDir, "avatar_decode_test.jpg")
        try {
            FileOutputStream(file).use { out ->
                assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 90, out))
            }
            val uri = android.net.Uri.fromFile(file)
            val decoded = AvatarImageProcessor.decodeSampled(context, uri)
            try {
                assertNotNull(decoded)
                assertTrue(decoded.width > 0)
                assertTrue(decoded.height > 0)
            } finally {
                decoded.recycle()
            }
        } finally {
            source.recycle()
            file.delete()
        }
    }

    @Test
    fun exportProducesSquareJpegUnderSizeLimit() {
        val source = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        source.eraseColor(0xFF4488FF.toInt())
        try {
            val bytes = AvatarImageProcessor.exportSquareJpeg(
                source = source,
                scale = 1f,
                offsetX = 0f,
                offsetY = 0f,
                viewportSize = 400f,
                filter = AvatarFilter.ORIGINAL
            )
            assertTrue(bytes.isNotEmpty())
            assertTrue(bytes.size < 2 * 1024 * 1024)
            // JPEG magic bytes
            assertEquals(0xFF.toByte(), bytes[0])
            assertEquals(0xD8.toByte(), bytes[1])
        } finally {
            source.recycle()
        }
    }

    @Test
    fun filtersProduceBitmapsWithoutCrashing() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        source.eraseColor(0xFFFF0000.toInt())
        try {
            AvatarFilter.entries.forEach { filter ->
                val out = AvatarImageProcessor.applyFilter(source, filter)
                assertEquals(64, out.width)
                assertEquals(64, out.height)
                if (out !== source) out.recycle()
            }
        } finally {
            source.recycle()
        }
    }
}
