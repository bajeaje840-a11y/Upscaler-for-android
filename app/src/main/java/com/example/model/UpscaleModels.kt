package com.example.model

import android.graphics.Bitmap
import android.net.Uri

enum class UpscaleAlgorithm(val displayName: String, val shortName: String, val description: String) {
    LANCZOS("Lanczos-3", "Lanczos", "Highest-fidelity windowed sinc interpolation. Preserves micro-contrast and edges."),
    BICUBIC("Bicubic Spline", "Bicubic", "Smooth cubic polynomial reconstruction. Classic photography standard."),
    BILINEAR("Bilinear", "Bilinear", "Fast linear interpolation. Balanced speed and sharpness."),
    NEAREST("Nearest Neighbor", "Nearest", "Direct pixel replication. Best for pixel art and technical schematics.")
}

enum class FilterLevel(val displayName: String, val strength: Float) {
    OFF("Off", 0.0f),
    LOW("Low", 0.35f),
    MEDIUM("Medium", 0.70f),
    HIGH("High", 1.20f)
}

enum class OutputFormat(val extension: String, val mimeType: String, val displayName: String) {
    PNG(".png", "image/png", "PNG (Lossless)"),
    JPEG(".jpg", "image/jpeg", "JPEG"),
    WEBP(".webp", "image/webp", "WebP");

    val isLossless: Boolean get() = this == PNG
}

data class UpscaleSettings(
    val scale: Int = 4, // 2x to 10x
    val algorithm: UpscaleAlgorithm = UpscaleAlgorithm.LANCZOS,
    val sharpening: FilterLevel = FilterLevel.MEDIUM,
    val noiseReduction: FilterLevel = FilterLevel.OFF,
    val detailEnhancement: FilterLevel = FilterLevel.LOW,
    val outputFormat: OutputFormat = OutputFormat.PNG,
    val jpegQuality: Int = 100, // 70 to 100
    val webpQuality: Int = 95,
    val preserveTransparency: Boolean = true,
    val maxConcurrentWorkers: Int = 2
)

enum class JobStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ImageJob(
    val id: String,
    val name: String,
    val uri: Uri? = null,
    val originalWidth: Int,
    val originalHeight: Int,
    val originalFileSize: Long,
    val originalMimeType: String = "image/jpeg",
    val status: JobStatus = JobStatus.QUEUED,
    val progress: Float = 0.0f,
    val stage: String = "Queued",
    val outputWidth: Int = originalWidth * 4,
    val outputHeight: Int = originalHeight * 4,
    val outputFileSize: Long = 0L,
    val outputPath: String? = null,
    val originalThumbnail: Bitmap? = null,
    val outputThumbnail: Bitmap? = null,
    val processingTimeMs: Long = 0L,
    val scaleUsed: Int = 4,
    val algorithmUsed: UpscaleAlgorithm = UpscaleAlgorithm.LANCZOS,
    val errorMessage: String? = null
)

data class MemorySafetyCheck(
    val isSafe: Boolean,
    val estimatedMemoryMb: Double,
    val totalMegapixels: Double,
    val warningMessage: String? = null
)
