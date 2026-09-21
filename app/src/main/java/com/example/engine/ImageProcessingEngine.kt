package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.model.FilterLevel
import com.example.model.ImageJob
import com.example.model.MemorySafetyCheck
import com.example.model.OutputFormat
import com.example.model.UpscaleAlgorithm
import com.example.model.UpscaleSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.system.measureTimeMillis

class ImageProcessingEngine(private val context: Context) {

    private val outputDir = File(context.cacheDir, "upscaled_images").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Estimates RAM requirement and validates against device limits.
     * 1 pixel = 4 bytes (ARGB_8888).
     */
    fun checkMemorySafety(originalWidth: Int, originalHeight: Int, scale: Int): MemorySafetyCheck {
        val outWidth = originalWidth.toLong() * scale
        val outHeight = originalHeight.toLong() * scale
        val totalPixels = outWidth * outHeight
        val megapixels = totalPixels / 1_000_000.0
        val bytes = totalPixels * 4L
        val memoryMb = bytes / (1024.0 * 1024.0)

        val isSafe = memoryMb <= 450.0 && outWidth <= 16384 && outHeight <= 16384
        val warningMessage = if (!isSafe) {
            "Output resolution (${outWidth}x${outHeight}, ${String.format("%.1f", megapixels)} MP) requires ~${String.format("%.0f", memoryMb)} MB of RAM. This may exceed device limits."
        } else null

        return MemorySafetyCheck(
            isSafe = isSafe,
            estimatedMemoryMb = memoryMb,
            totalMegapixels = megapixels,
            warningMessage = warningMessage
        )
    }

    /**
     * Decodes original image bitmap without downscaling to ensure zero loss before processing.
     */
    suspend fun decodeOriginalBitmap(job: ImageJob): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (job.uri != null) {
                context.contentResolver.openInputStream(job.uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else if (job.outputPath != null) {
                BitmapFactory.decodeFile(job.outputPath)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Executes the complete processing pipeline:
     * Decode -> Resample (Lanczos/Bicubic/Bilinear/Nearest) -> Sharpen -> Denoise -> Clarity -> Encode -> Cache
     */
    suspend fun processJob(
        job: ImageJob,
        settings: UpscaleSettings,
        onProgress: (progress: Float, stage: String) -> Unit
    ): ProcessResult = withContext(Dispatchers.Default) {
        onProgress(0.05f, "Decoding source image...")
        val srcBitmap = decodeOriginalBitmap(job)
            ?: throw IllegalStateException("Failed to decode image file '${job.name}'")

        val targetWidth = srcBitmap.width * settings.scale
        val targetHeight = srcBitmap.height * settings.scale

        onProgress(0.15f, "Resampling with ${settings.algorithm.displayName} (${settings.scale}×)...")

        val resampledBitmap: Bitmap
        val elapsedMs = measureTimeMillis {
            resampledBitmap = when (settings.algorithm) {
                UpscaleAlgorithm.LANCZOS -> LanczosResampler.resize(
                    srcBitmap,
                    targetWidth,
                    targetHeight
                ) { p ->
                    onProgress(0.15f + p * 0.45f, "Resampling (${(p * 100).toInt()}%)")
                }

                UpscaleAlgorithm.BICUBIC -> BicubicResampler.resize(
                    srcBitmap,
                    targetWidth,
                    targetHeight
                ) { p ->
                    onProgress(0.15f + p * 0.45f, "Bicubic Resampling (${(p * 100).toInt()}%)")
                }

                UpscaleAlgorithm.BILINEAR -> ImageFilters.bilinearResize(
                    srcBitmap,
                    targetWidth,
                    targetHeight
                ) { p ->
                    onProgress(0.15f + p * 0.45f, "Bilinear Resampling")
                }

                UpscaleAlgorithm.NEAREST -> ImageFilters.nearestResize(
                    srcBitmap,
                    targetWidth,
                    targetHeight
                ) { p ->
                    onProgress(0.15f + p * 0.45f, "Nearest Neighbor Resampling")
                }
            }
        }

        // Apply filters sequentially
        var filteredBitmap = resampledBitmap

        if (settings.noiseReduction != FilterLevel.OFF) {
            onProgress(0.65f, "Applying bilateral noise reduction...")
            val denoised = ImageFilters.applyNoiseReduction(filteredBitmap, settings.noiseReduction)
            if (denoised !== filteredBitmap && filteredBitmap !== resampledBitmap) {
                filteredBitmap.recycle()
            }
            filteredBitmap = denoised
        }

        if (settings.sharpening != FilterLevel.OFF) {
            onProgress(0.75f, "Applying edge-preserving sharpening...")
            val sharpened = ImageFilters.applySharpening(filteredBitmap, settings.sharpening)
            if (sharpened !== filteredBitmap && filteredBitmap !== resampledBitmap) {
                filteredBitmap.recycle()
            }
            filteredBitmap = sharpened
        }

        if (settings.detailEnhancement != FilterLevel.OFF) {
            onProgress(0.85f, "Enhancing micro-contrast clarity...")
            val enhanced = ImageFilters.applyDetailEnhancement(filteredBitmap, settings.detailEnhancement)
            if (enhanced !== filteredBitmap && filteredBitmap !== resampledBitmap) {
                filteredBitmap.recycle()
            }
            filteredBitmap = enhanced
        }

        onProgress(0.90f, "Encoding output (${settings.outputFormat.displayName})...")

        // Save output to cache
        val baseName = job.name.substringBeforeLast(".")
        val outFileName = "${baseName}_${settings.scale}x_${settings.algorithm.shortName.lowercase()}${settings.outputFormat.extension}"
        val outFile = File(outputDir, outFileName)

        withContext(Dispatchers.IO) {
            FileOutputStream(outFile).use { fos ->
                when (settings.outputFormat) {
                    OutputFormat.PNG -> {
                        filteredBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                    OutputFormat.JPEG -> {
                        filteredBitmap.compress(Bitmap.CompressFormat.JPEG, settings.jpegQuality.coerceIn(70, 100), fos)
                    }
                    OutputFormat.WEBP -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            if (settings.preserveTransparency) {
                                filteredBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, fos)
                            } else {
                                filteredBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, settings.webpQuality, fos)
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            filteredBitmap.compress(Bitmap.CompressFormat.WEBP, settings.webpQuality, fos)
                        }
                    }
                }
            }
        }

        onProgress(0.96f, "Generating preview thumbnail...")
        val thumbnail = createThumbnail(filteredBitmap, 900)

        // Clean up full bitmap memory if not needed
        if (filteredBitmap !== thumbnail) {
            filteredBitmap.recycle()
        }
        if (resampledBitmap !== filteredBitmap) {
            resampledBitmap.recycle()
        }

        onProgress(1.0f, "Completed in ${elapsedMs}ms")

        ProcessResult(
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            outputFile = outFile,
            outputFileSize = outFile.length(),
            thumbnail = thumbnail,
            elapsedTimeMs = elapsedMs
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
        val elapsedTimeMs: Long
    )
}
