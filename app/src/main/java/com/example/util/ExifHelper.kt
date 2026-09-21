package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object ExifHelper {

    /**
     * Reads EXIF orientation from Uri or File path.
     */
    fun getOrientation(context: Context, uri: Uri?, filePath: String?): Int {
        try {
            val stream: InputStream? = when {
                uri != null -> context.contentResolver.openInputStream(uri)
                filePath != null && File(filePath).exists() -> FileInputStream(filePath)
                else -> null
            }
            return stream?.use {
                val exif = ExifInterface(it)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Throwable) {
            return ExifInterface.ORIENTATION_NORMAL
        }
    }

    /**
     * Returns true width and height considering EXIF orientation.
     * When rotated 90 or 270 degrees, dimensions are swapped so that landscape/portrait
     * aspect ratios are always 100% mathematically correct.
     */
    fun getTrueDimensions(context: Context, uri: Uri?, filePath: String?): Pair<Int, Int> {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            val stream: InputStream? = when {
                uri != null -> context.contentResolver.openInputStream(uri)
                filePath != null && File(filePath).exists() -> FileInputStream(filePath)
                else -> null
            }
            stream?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }
        } catch (_: Throwable) {
            return Pair(1, 1)
        }

        val rawW = boundsOptions.outWidth.coerceAtLeast(1)
        val rawH = boundsOptions.outHeight.coerceAtLeast(1)

        val orientation = getOrientation(context, uri, filePath)
        val isRotated90or270 = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE

        return if (isRotated90or270) {
            Pair(rawH, rawW)
        } else {
            Pair(rawW, rawH)
        }
    }

    /**
     * Decodes the bitmap and applies necessary EXIF rotation / flips so the resulting
     * bitmap is upright and matches the visual aspect ratio.
     */
    fun decodeOrientedBitmap(
        context: Context,
        uri: Uri?,
        filePath: String?,
        sampleSize: Int = 1
    ): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val orientation = getOrientation(context, uri, filePath)
        val rawBitmap: Bitmap? = try {
            val stream: InputStream? = when {
                uri != null -> context.contentResolver.openInputStream(uri)
                filePath != null && File(filePath).exists() -> FileInputStream(filePath)
                else -> null
            }
            stream?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (_: Throwable) {
            null
        }

        if (rawBitmap == null) return null

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return rawBitmap
        }

        return try {
            val oriented = Bitmap.createBitmap(
                rawBitmap,
                0,
                0,
                rawBitmap.width,
                rawBitmap.height,
                matrix,
                true
            )
            if (oriented !== rawBitmap) {
                rawBitmap.recycle()
            }
            oriented
        } catch (_: Throwable) {
            rawBitmap
        }
    }
}
