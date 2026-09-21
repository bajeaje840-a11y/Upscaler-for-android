package com.example.engine

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs

object BicubicResampler {

    // Catmull-Rom spline kernel: a = -0.5
    private fun cubic(x: Double): Double {
        val ax = abs(x)
        return when {
            ax <= 1.0 -> 1.5 * ax * ax * ax - 2.5 * ax * ax + 1.0
            ax < 2.0 -> -0.5 * ax * ax * ax + 2.5 * ax * ax - 4.0 * ax + 2.0
            else -> 0.0
        }
    }

    suspend fun resize(
        src: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        onProgress: (Float) -> Unit
    ): Bitmap = coroutineScope {
        val srcWidth = src.width
        val srcHeight = src.height

        val scaleX = targetWidth.toDouble() / srcWidth.toDouble()
        val hIndices = Array(targetWidth) { IntArray(4) }
        val hWeights = Array(targetWidth) { DoubleArray(4) }

        for (x in 0 until targetWidth) {
            val center = (x + 0.5) / scaleX - 0.5
            val x0 = kotlin.math.floor(center).toInt()
            var sumWeight = 0.0
            for (i in 0 until 4) {
                val srcIdx = (x0 - 1 + i).coerceIn(0, srcWidth - 1)
                val dist = srcIdx - center
                val w = cubic(dist)
                hIndices[x][i] = srcIdx
                hWeights[x][i] = w
                sumWeight += w
            }
            if (sumWeight != 0.0) {
                for (i in 0 until 4) hWeights[x][i] /= sumWeight
            }
        }

        val srcPixels = IntArray(srcWidth * srcHeight)
        src.getPixels(srcPixels, 0, srcWidth, 0, 0, srcWidth, srcHeight)

        val intermediate = IntArray(targetWidth * srcHeight)

        val hChunk = kotlin.math.max(1, srcHeight / 4)
        val hJobs = (0 until srcHeight step hChunk).map { startY ->
            val endY = kotlin.math.min(srcHeight, startY + hChunk)
            async(Dispatchers.Default) {
                for (y in startY until endY) {
                    val rowOff = y * srcWidth
                    val outOff = y * targetWidth
                    for (x in 0 until targetWidth) {
                        var a = 0.0
                        var r = 0.0
                        var g = 0.0
                        var b = 0.0
                        val indices = hIndices[x]
                        val weights = hWeights[x]
                        for (i in 0 until 4) {
                            val pixel = srcPixels[rowOff + indices[i]]
                            val w = weights[i]
                            a += ((pixel ushr 24) and 0xFF) * w
                            r += ((pixel ushr 16) and 0xFF) * w
                            g += ((pixel ushr 8) and 0xFF) * w
                            b += (pixel and 0xFF) * w
                        }
                        intermediate[outOff + x] = ((a.toInt().coerceIn(0, 255)) shl 24) or
                                ((r.toInt().coerceIn(0, 255)) shl 16) or
                                ((g.toInt().coerceIn(0, 255)) shl 8) or
                                (b.toInt().coerceIn(0, 255))
                    }
                }
            }
        }
        hJobs.awaitAll()
        onProgress(0.5f)

        val scaleY = targetHeight.toDouble() / srcHeight.toDouble()
        val vIndices = Array(targetHeight) { IntArray(4) }
        val vWeights = Array(targetHeight) { DoubleArray(4) }

        for (y in 0 until targetHeight) {
            val center = (y + 0.5) / scaleY - 0.5
            val y0 = kotlin.math.floor(center).toInt()
            var sumWeight = 0.0
            for (i in 0 until 4) {
                val srcIdx = (y0 - 1 + i).coerceIn(0, srcHeight - 1)
                val dist = srcIdx - center
                val w = cubic(dist)
                vIndices[y][i] = srcIdx
                vWeights[y][i] = w
                sumWeight += w
            }
            if (sumWeight != 0.0) {
                for (i in 0 until 4) vWeights[y][i] /= sumWeight
            }
        }

        val outputPixels = IntArray(targetWidth * targetHeight)
        val vChunk = kotlin.math.max(1, targetHeight / 4)
        val vJobs = (0 until targetHeight step vChunk).map { startY ->
            val endY = kotlin.math.min(targetHeight, startY + vChunk)
            async(Dispatchers.Default) {
                for (y in startY until endY) {
                    val outOff = y * targetWidth
                    val indices = vIndices[y]
                    val weights = vWeights[y]
                    for (x in 0 until targetWidth) {
                        var a = 0.0
                        var r = 0.0
                        var g = 0.0
                        var b = 0.0
                        for (i in 0 until 4) {
                            val pixel = intermediate[indices[i] * targetWidth + x]
                            val w = weights[i]
                            a += ((pixel ushr 24) and 0xFF) * w
                            r += ((pixel ushr 16) and 0xFF) * w
                            g += ((pixel ushr 8) and 0xFF) * w
                            b += (pixel and 0xFF) * w
                        }
                        outputPixels[outOff + x] = ((a.toInt().coerceIn(0, 255)) shl 24) or
                                ((r.toInt().coerceIn(0, 255)) shl 16) or
                                ((g.toInt().coerceIn(0, 255)) shl 8) or
                                (b.toInt().coerceIn(0, 255))
                    }
                }
            }
        }
        vJobs.awaitAll()
        onProgress(1.0f)

        Bitmap.createBitmap(outputPixels, targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    }
}
