package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.PI
import kotlin.math.sin

object LanczosResampler {

    private const val RADIUS = 3.0 // Lanczos-3 window

    private fun sinc(x: Double): Double {
        if (x == 0.0) return 1.0
        val px = x * PI
        return sin(px) / px
    }

    private fun lanczos(x: Double): Double {
        val ax = kotlin.math.abs(x)
        if (ax >= RADIUS) return 0.0
        return sinc(ax) * sinc(ax / RADIUS)
    }

    suspend fun resize(
        src: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        onProgress: (Float) -> Unit
    ): Bitmap = coroutineScope {
        val srcWidth = src.width
        val srcHeight = src.height

        // Precompute horizontal weights and sample indices for each target column
        val scaleX = targetWidth.toDouble() / srcWidth.toDouble()
        val filterRadiusX = RADIUS / if (scaleX < 1.0) scaleX else 1.0

        val hIndices = Array(targetWidth) { IntArray(12) }
        val hWeights = Array(targetWidth) { DoubleArray(12) }
        val hCounts = IntArray(targetWidth)

        for (x in 0 until targetWidth) {
            val center = (x + 0.5) / scaleX - 0.5
            val minIdx = kotlin.math.max(0, kotlin.math.floor(center - filterRadiusX).toInt())
            val maxIdx = kotlin.math.min(srcWidth - 1, kotlin.math.ceil(center + filterRadiusX).toInt())

            var sumWeight = 0.0
            var count = 0
            for (srcX in minIdx..maxIdx) {
                if (count >= 12) break
                val dist = (srcX - center) * (if (scaleX < 1.0) scaleX else 1.0)
                val w = lanczos(dist)
                hIndices[x][count] = srcX
                hWeights[x][count] = w
                sumWeight += w
                count++
            }
            hCounts[x] = count
            // Normalize weights so luminance is preserved
            if (sumWeight != 0.0) {
                for (i in 0 until count) {
                    hWeights[x][i] /= sumWeight
                }
            }
        }

        // Extract source pixel data
        val srcPixels = IntArray(srcWidth * srcHeight)
        src.getPixels(srcPixels, 0, srcWidth, 0, 0, srcWidth, srcHeight)

        // Intermediate buffer: targetWidth x srcHeight
        val intermediate = IntArray(targetWidth * srcHeight)

        // Pass 1: Horizontal resampling
        val hChunkSize = kotlin.math.max(1, srcHeight / 4)
        val hJobs = (0 until srcHeight step hChunkSize).map { startY ->
            val endY = kotlin.math.min(srcHeight, startY + hChunkSize)
            async(Dispatchers.Default) {
                for (y in startY until endY) {
                    val rowOffset = y * srcWidth
                    val outOffset = y * targetWidth
                    for (x in 0 until targetWidth) {
                        var a = 0.0
                        var r = 0.0
                        var g = 0.0
                        var b = 0.0
                        val count = hCounts[x]
                        val indices = hIndices[x]
                        val weights = hWeights[x]
                        for (i in 0 until count) {
                            val pixel = srcPixels[rowOffset + indices[i]]
                            val w = weights[i]
                            a += ((pixel ushr 24) and 0xFF) * w
                            r += ((pixel ushr 16) and 0xFF) * w
                            g += ((pixel ushr 8) and 0xFF) * w
                            b += (pixel and 0xFF) * w
                        }
                        val finalA = a.toInt().coerceIn(0, 255)
                        val finalR = r.toInt().coerceIn(0, 255)
                        val finalG = g.toInt().coerceIn(0, 255)
                        val finalB = b.toInt().coerceIn(0, 255)
                        intermediate[outOffset + x] = (finalA shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                    }
                }
            }
        }
        hJobs.awaitAll()
        onProgress(0.5f)

        // Precompute vertical weights and sample indices for each target row
        val scaleY = targetHeight.toDouble() / srcHeight.toDouble()
        val filterRadiusY = RADIUS / if (scaleY < 1.0) scaleY else 1.0

        val vIndices = Array(targetHeight) { IntArray(12) }
        val vWeights = Array(targetHeight) { DoubleArray(12) }
        val vCounts = IntArray(targetHeight)

        for (y in 0 until targetHeight) {
            val center = (y + 0.5) / scaleY - 0.5
            val minIdx = kotlin.math.max(0, kotlin.math.floor(center - filterRadiusY).toInt())
            val maxIdx = kotlin.math.min(srcHeight - 1, kotlin.math.ceil(center + filterRadiusY).toInt())

            var sumWeight = 0.0
            var count = 0
            for (srcY in minIdx..maxIdx) {
                if (count >= 12) break
                val dist = (srcY - center) * (if (scaleY < 1.0) scaleY else 1.0)
                val w = lanczos(dist)
                vIndices[y][count] = srcY
                vWeights[y][count] = w
                sumWeight += w
                count++
            }
            vCounts[y] = count
            if (sumWeight != 0.0) {
                for (i in 0 until count) {
                    vWeights[y][i] /= sumWeight
                }
            }
        }

        // Output buffer: targetWidth x targetHeight
        val outputPixels = IntArray(targetWidth * targetHeight)

        // Pass 2: Vertical resampling
        val vChunkSize = kotlin.math.max(1, targetHeight / 4)
        val vJobs = (0 until targetHeight step vChunkSize).map { startY ->
            val endY = kotlin.math.min(targetHeight, startY + vChunkSize)
            async(Dispatchers.Default) {
                for (y in startY until endY) {
                    val outRowOffset = y * targetWidth
                    val count = vCounts[y]
                    val indices = vIndices[y]
                    val weights = vWeights[y]

                    for (x in 0 until targetWidth) {
                        var a = 0.0
                        var r = 0.0
                        var g = 0.0
                        var b = 0.0
                        for (i in 0 until count) {
                            val pixel = intermediate[indices[i] * targetWidth + x]
                            val w = weights[i]
                            a += ((pixel ushr 24) and 0xFF) * w
                            r += ((pixel ushr 16) and 0xFF) * w
                            g += ((pixel ushr 8) and 0xFF) * w
                            b += (pixel and 0xFF) * w
                        }
                        val finalA = a.toInt().coerceIn(0, 255)
                        val finalR = r.toInt().coerceIn(0, 255)
                        val finalG = g.toInt().coerceIn(0, 255)
                        val finalB = b.toInt().coerceIn(0, 255)
                        outputPixels[outRowOffset + x] = (finalA shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                    }
                }
            }
        }
        vJobs.awaitAll()
        onProgress(1.0f)

        Bitmap.createBitmap(outputPixels, targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    }
}
