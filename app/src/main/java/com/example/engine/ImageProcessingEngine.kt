package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.model.FilterLevel
import com.example.model.ImageJob
import com.example.model.MemorySafetyCheck
import com.example.model.OutputFormat
import com.example.model.UpscaleAlgorithm
import com.example.model.UpscaleSettings
import com.example.util.ExifHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.system.measureTimeMillis

class ImageProcessingEngine(private val context: Context) {

    private val outputDir = File(context.cacheDir, "upscaled_images").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Estimates RAM requirement and validates against device limits.
     * 1 pixel = 4 bytes (ARGB_8888).
     * Maximum dimension limit: 16,384 px (Android Skia / Canvas hardware limit).
     */
    fun checkMemorySafety(originalWidth: Int, originalHeight: Int, scale: Int): MemorySafetyCheck {
        val outWidth = originalWidth.toLong() * scale
        val outHeight = originalHeight.toLong() * scale
        val totalPixels = outWidth * outHeight
        val megapixels = totalPixels / 1_000_000.0
        val bytes = totalPixels * 4L
        val memoryMb = bytes / (1024.0 * 1024.0)

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val availMemBytes = if (memInfo.availMem > 0L) memInfo.availMem else Runtime.getRuntime().maxMemory()
        val availMemMb = availMemBytes / (1024.0 * 1024.0)

        val exceedsDimension = outWidth > 16384 || outHeight > 16384
        val exceedsMemory = memoryMb > 600.0 || (memoryMb > availMemMb * 0.75)

        val isSafe = !exceedsDimension && !exceedsMemory
        val warningMessage = when {
            exceedsDimension -> "${scale}× output (${outWidth} × ${outHeight}) exceeds the device maximum dimension limit of 16,384 px."
            exceedsMemory -> "${scale}× output (${outWidth} × ${outHeight}, ${String.format(Locale.US, "%.1f", megapixels)} MP) is too large for this device's available memory (~${String.format(Locale.US, "%.0f", memoryMb)} MB required)."
            else -> null
        }

        return MemorySafetyCheck(
            isSafe = isSafe,
            estimatedMemoryMb = memoryMb,
            totalMegapixels = megapixels,
            warningMessage = warningMessage
        )
    }

    /**
     * Decodes source image bitmap with correct EXIF orientation and full precision.
     */
    suspend fun decodeOriginalBitmap(job: ImageJob): Bitmap? = withContext(Dispatchers.IO) {
        try {
            ExifHelper.decodeOrientedBitmap(context, job.uri, job.outputPath)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Executes the complete image upscaling pipeline:
     * Decode -> Verify exact dimensions -> Strip-based Resampling -> Filters -> Encode -> Cleanup
     */
    suspend fun processJob(
        job: ImageJob,
        settings: UpscaleSettings,
        onProgress: (progress: Float, stage: String) -> Unit
    ): ProcessResult = withContext(Dispatchers.Default) {
        onProgress(0.04f, "Decoding source image...")
        val srcBitmap = decodeOriginalBitmap(job)
            ?: throw IllegalStateException("Failed to decode image file '${job.name}'")

        val origW = srcBitmap.width
        val origH = srcBitmap.height

        // Calculate exact target dimensions: NEVER crop, NEVER distort aspect ratio
        val targetWidth = origW * settings.scale
        val targetHeight = origH * settings.scale

        if (targetWidth <= 0 || targetHeight <= 0) {
            srcBitmap.recycle()
            throw IllegalStateException("Invalid target dimensions: ${targetWidth} × $targetHeight")
        }

        if (targetWidth > 16384 || targetHeight > 16384) {
            srcBitmap.recycle()
            throw IllegalStateException("${settings.scale}× output (${targetWidth} × ${targetHeight}) exceeds the device maximum canvas dimension limit of 16,384 pixels.")
        }

        val requiredBytes = targetWidth.toLong() * targetHeight.toLong() * 4L
        val requiredMb = requiredBytes / (1024.0 * 1024.0)
        val megapixels = (targetWidth.toLong() * targetHeight) / 1_000_000.0

        // Device RAM check
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val availMemBytes = if (memInfo.availMem > 0L) memInfo.availMem else Runtime.getRuntime().maxMemory()
        val availMemMb = availMemBytes / (1024.0 * 1024.0)

        if (requiredMb > 600.0 || (requiredMb > availMemMb * 0.80)) {
            srcBitmap.recycle()
            throw IllegalStateException("${settings.scale}× output (${targetWidth} × ${targetHeight}, ${String.format(Locale.US, "%.1f", megapixels)} MP) is too large for this device's available memory (~${String.format(Locale.US, "%.0f", requiredMb)} MB required).")
        }

        onProgress(0.10f, "Resampling to ${targetWidth} × ${targetHeight} (${settings.scale}×)...")

        var resampledBitmap: Bitmap? = null
        val elapsedMs: Long

        try {
            elapsedMs = measureTimeMillis {
                resampledBitmap = when (settings.algorithm) {
                    UpscaleAlgorithm.LANCZOS -> LanczosResampler.resize(
                        srcBitmap,
                        targetWidth,
                        targetHeight
                    ) { p ->
                        onProgress(0.10f + p * 0.50f, "Lanczos Resampling (${(p * 100).toInt()}%)")
                    }

                    UpscaleAlgorithm.BICUBIC -> BicubicResampler.resize(
                        srcBitmap,
                        targetWidth,
                        targetHeight
                    ) { p ->
                        onProgress(0.10f + p * 0.50f, "Bicubic Resampling (${(p * 100).toInt()}%)")
                    }

                    UpscaleAlgorithm.BILINEAR -> ImageFilters.bilinearResize(
                        srcBitmap,
                        targetWidth,
                        targetHeight
                    ) { p ->
                        onProgress(0.10f + p * 0.50f, "Bilinear Resampling (${(p * 100).toInt()}%)")
                    }

                    UpscaleAlgorithm.NEAREST -> ImageFilters.nearestResize(
                        srcBitmap,
                        targetWidth,
                        targetHeight
                    ) { p ->
                        onProgress(0.10f + p * 0.50f, "Nearest Resampling (${(p * 100).toInt()}%)")
                    }
                }
            }
        } catch (oom: OutOfMemoryError) {
            srcBitmap.recycle()
            resampledBitmap?.recycle()
            System.gc()
            throw IllegalStateException("${settings.scale}× output is too large for this device's available memory.")
        } finally {
            // Free original source bitmap immediately
            srcBitmap.recycle()
        }

        var currentBitmap = resampledBitmap ?: throw IllegalStateException("Resampling produced null bitmap")

        // Apply filters sequentially with strip-based processing
        if (settings.noiseReduction != FilterLevel.OFF) {
            onProgress(0.65f, "Applying bilateral noise reduction...")
            val denoised = ImageFilters.applyNoiseReduction(currentBitmap, settings.noiseReduction)
            if (denoised !== currentBitmap) {
                currentBitmap.recycle()
                currentBitmap = denoised
            }
        }

        if (settings.sharpening != FilterLevel.OFF) {
            onProgress(0.75f, "Applying edge-preserving sharpening...")
            val sharpened = ImageFilters.applySharpening(currentBitmap, settings.sharpening)
            if (sharpened !== currentBitmap) {
                currentBitmap.recycle()
                currentBitmap = sharpened
            }
        }

        if (settings.detailEnhancement != FilterLevel.OFF) {
            onProgress(0.85f, "Enhancing micro-contrast clarity...")
            val enhanced = ImageFilters.applyDetailEnhancement(currentBitmap, settings.detailEnhancement)
            if (enhanced !== currentBitmap) {
                currentBitmap.recycle()
                currentBitmap = enhanced
            }
        }

        // Determine effective output format and quality
        val effectiveFormat = when (settings.outputFormat) {
            OutputFormat.AUTO -> {
                val mime = job.originalMimeType.lowercase(Locale.ROOT)
                val name = job.name.lowercase(Locale.ROOT)
                when {
                    mime.contains("png") || name.endsWith(".png") || (settings.preserveTransparency && currentBitmap.hasAlpha()) -> OutputFormat.PNG
                    mime.contains("webp") || name.endsWith(".webp") -> OutputFormat.WEBP
                    else -> OutputFormat.JPEG
                }
            }
            OutputFormat.JPEG -> OutputFormat.JPEG
            OutputFormat.PNG -> OutputFormat.PNG
            OutputFormat.WEBP -> OutputFormat.WEBP
        }

        val (effectiveQuality, formatExtension, formatLabel) = when (effectiveFormat) {
            OutputFormat.JPEG -> {
                val q = if (settings.optimizeFileSize) {
                    (settings.jpegQuality - 5).coerceIn(85, 93)
                } else {
                    settings.jpegQuality.coerceIn(70, 100)
                }
                Triple(q, ".jpg", "JPG")
            }
            OutputFormat.PNG -> {
                Triple(100, ".png", "PNG")
            }
            OutputFormat.WEBP -> {
                val q = if (settings.optimizeFileSize) {
                    (settings.webpQuality - 7).coerceIn(80, 92)
                } else {
                    settings.webpQuality.coerceIn(70, 100)
                }
                Triple(q, ".webp", "WebP")
            }
            OutputFormat.AUTO -> Triple(settings.jpegQuality.coerceIn(70, 100), ".jpg", "JPG")
        }

        onProgress(0.90f, "Encoding output ($formatLabel at $effectiveQuality% quality)...")

        // Save output to cache
        val baseName = job.name.substringBeforeLast(".")
        val outFileName = "${baseName}_${settings.scale}x_${settings.algorithm.shortName.lowercase(Locale.ROOT)}${if (settings.optimizeFileSize) "_opt" else ""}$formatExtension"
        val outFile = File(outputDir, outFileName)

        withContext(Dispatchers.IO) {
            java.io.BufferedOutputStream(FileOutputStream(outFile), 64 * 1024).use { bos ->
                when (effectiveFormat) {
                    OutputFormat.PNG -> {
                        currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
                    }
                    OutputFormat.JPEG -> {
                        if (currentBitmap.hasAlpha()) {
                            val whiteBg = Bitmap.createBitmap(currentBitmap.width, currentBitmap.height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(whiteBg)
                            canvas.drawColor(Color.WHITE)
                            canvas.drawBitmap(currentBitmap, 0f, 0f, Paint())
                            whiteBg.compress(Bitmap.CompressFormat.JPEG, effectiveQuality, bos)
                            whiteBg.recycle()
                        } else {
                            currentBitmap.compress(Bitmap.CompressFormat.JPEG, effectiveQuality, bos)
                        }
                    }
                    OutputFormat.WEBP -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            if (currentBitmap.hasAlpha() && settings.preserveTransparency) {
                                currentBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, bos)
                            } else {
                                currentBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, effectiveQuality, bos)
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            currentBitmap.compress(Bitmap.CompressFormat.WEBP, effectiveQuality, bos)
                        }
                    }
                    OutputFormat.AUTO -> {
                        currentBitmap.compress(Bitmap.CompressFormat.JPEG, effectiveQuality, bos)
                    }
                }
                bos.flush()
            }
        }

        onProgress(0.96f, "Generating preview thumbnail...")
        val thumbnail = createThumbnail(currentBitmap, 900)

        // Free full resolution bitmap immediately after saving and thumbnailing
        currentBitmap.recycle()
        System.gc()

        onProgress(1.0f, "Completed in ${elapsedMs}ms")

        ProcessResult(
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            outputFile = outFile,
            outputFileSize = outFile.length(),
            thumbnail = thumbnail,
            elapsedTimeMs = elapsedMs,
            formatUsed = formatLabel,
            qualityUsed = effectiveQuality,
            isOptimized = settings.optimizeFileSize
        )
    }

    /**
     * Creates a high-fidelity scaled thumbnail for smooth UI previews without memory crashes.
     */
    fun createThumbnail(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDimension && h <= maxDimension) return bitmap.copy(Bitmap.Config.ARGB_8888, false)

        val ratio = if (w > h) maxDimension.toFloat() / w else maxDimension.toFloat() / h
        val tw = (w * ratio).toInt().coerceAtLeast(1)
        val th = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, tw, th, true)
    }

    data class ProcessResult(
        val targetWidth: Int,
        val targetHeight: Int,
        val outputFile: File,
        val outputFileSize: Long,
        val thumbnail: Bitmap,
        val elapsedTimeMs: Long,
        val formatUsed: String = "JPG",
        val qualityUsed: Int = 95,
        val isOptimized: Boolean = false
    )
}
