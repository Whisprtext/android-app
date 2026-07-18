package com.whisprtext.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class AvatarFilter {
    ORIGINAL,
    GRAYSCALE,
    WARM,
    COOL
}

/**
 * Decode, orient, crop-to-square, filter, and compress gallery images for avatar upload.
 * Downsamples large images before editing to avoid OOM.
 */
object AvatarImageProcessor {

    const val OUTPUT_SIZE = 512
    const val JPEG_QUALITY = 85
    private const val MAX_DECODE_DIM = 2048

    fun decodeSampled(context: Context, uri: Uri): Bitmap {
        // Prefer the activity/context that received the Photo Picker grant (not applicationContext).
        val resolver = context.contentResolver

        // Bounds pass: decodeStream returns null when inJustDecodeBounds=true — that is expected.
        // Do not use `openInputStream()?.use { decode... } ?: throw`, because the null bitmap
        // would be misread as a failed open.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Unable to open image")
        boundsStream.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("Unsupported or corrupt image")
        }

        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DECODE_DIM)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decodeStream = resolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Unable to reopen image")
        val decoded = decodeStream.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        } ?: throw IllegalArgumentException("Unable to decode image")

        return applyExifOrientation(context, uri, decoded)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxDim || h / 2 >= maxDim) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return max(1, sample)
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (oriented !== bitmap) {
            bitmap.recycle()
        }
        return oriented
    }

    /**
     * Export a square JPEG from the source bitmap given a normalized crop window.
     *
     * @param scale pinch-zoom scale relative to the fitted image inside the viewport
     * @param offsetX pan X in viewport pixels (center-based)
     * @param offsetY pan Y in viewport pixels (center-based)
     * @param viewportSize size of the square viewport in pixels
     */
    fun exportSquareJpeg(
        source: Bitmap,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        viewportSize: Float,
        filter: AvatarFilter
    ): ByteArray {
        val fitted = fitCenterScale(source.width, source.height, viewportSize)
        val totalScale = fitted * scale

        // Map viewport square back into source image coordinates.
        val srcCenterX = source.width / 2f - offsetX / totalScale
        val srcCenterY = source.height / 2f - offsetY / totalScale
        val half = (viewportSize / totalScale) / 2f

        var left = (srcCenterX - half).roundToInt()
        var top = (srcCenterY - half).roundToInt()
        var right = (srcCenterX + half).roundToInt()
        var bottom = (srcCenterY + half).roundToInt()

        // Clamp crop rect to source bounds.
        left = left.coerceIn(0, source.width - 1)
        top = top.coerceIn(0, source.height - 1)
        right = right.coerceIn(left + 1, source.width)
        bottom = bottom.coerceIn(top + 1, source.height)

        val side = min(right - left, bottom - top)
        right = left + side
        bottom = top + side

        val cropped = Bitmap.createBitmap(source, left, top, side, side)
        val sized = if (side != OUTPUT_SIZE) {
            cropped.scale(OUTPUT_SIZE, OUTPUT_SIZE).also {
                if (it !== cropped) cropped.recycle()
            }
        } else {
            cropped
        }

        val filtered = applyFilter(sized, filter)
        if (filtered !== sized) {
            sized.recycle()
        }

        val out = ByteArrayOutputStream()
        if (!filtered.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
            filtered.recycle()
            throw IllegalStateException("Failed to compress avatar image")
        }
        filtered.recycle()
        return out.toByteArray()
    }

    private fun fitCenterScale(srcW: Int, srcH: Int, viewport: Float): Float {
        return max(viewport / srcW, viewport / srcH)
    }

    fun applyFilter(source: Bitmap, filter: AvatarFilter): Bitmap {
        if (filter == AvatarFilter.ORIGINAL) return source
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val cm = when (filter) {
            AvatarFilter.GRAYSCALE -> ColorMatrix().apply { setSaturation(0f) }
            AvatarFilter.WARM -> ColorMatrix(
                floatArrayOf(
                    1.15f, 0f, 0f, 0f, 12f,
                    0f, 1.05f, 0f, 0f, 6f,
                    0f, 0f, 0.9f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            AvatarFilter.COOL -> ColorMatrix(
                floatArrayOf(
                    0.9f, 0f, 0f, 0f, 0f,
                    0f, 1.0f, 0f, 0f, 4f,
                    0f, 0f, 1.15f, 0f, 12f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            AvatarFilter.ORIGINAL -> return source
        }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }
}
