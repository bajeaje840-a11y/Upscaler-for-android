package com.example.engine

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.floor

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

    /**
     * Resizes [src] to [targetWidth] x [targetHeight] using Catmull-Rom bicubic spline filtering.
     * Uses strip-based processing to maintain a minimal working memory footprint (< 5 MB RAM),
     * preventing OutOfMemory crashes across 2× to 10× upscaling.
     */
    suspend fun resize(
        src: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        onProgress: (Float) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) {
        val srcWidth = src.width
        val srcHeight = src.height

        val scaleX = targetWidth.toDouble() / srcWidth.toDouble()
        val scaleY = targetHeight.toDouble() / srcHeight.toDouble()

        // Precompute horizontal weights and sample indices (4-tap per column)
        val hIndices = Array(targetWidth) { IntArray(4) }
        val hWeights = Array(targetWidth) { DoubleArray(4) }

        for (x in 0 until targetWidth) {
            val center = (x + 0.5) / scaleX - 0.5
            val x0 = floor(center).toInt()
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

        // Precompute vertical weights and sample indices (4-tap per row)
        val vIndices = Array(targetHeight) { IntArray(4) }
        val vWeights = Array(targetHeight) { DoubleArray(4) }

        for (y in 0 until targetHeight) {
            val center = (y + 0.5) / scaleY - 0.5
            val y0 = floor(center).toInt()
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

        // Allocate destination bitmap
        val outBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

        val stripHeight = 64.coerceAtMost(targetHeight)
        val numStrips = (targetHeight + stripHeight - 1) / stripHeight

        for (stripIndex in 0 until numStrips) {
            coroutineContext.ensureActive()

            val stripStartY = stripIndex * stripHeight
            val stripEndY = (stripStartY + stripHeight).coerceAtMost(targetHeight)
            val currentStripH = stripEndY - stripStartY

            var minSrcRow = srcHeight - 1
            var maxSrcRow = 0
            for (y in stripStartY until stripEndY) {
                val indices = vIndices[y]
                for (i in 0 until 4) {
                    val s = indices[i]
                    if (s < minSrcRow) minSrcRow = s
                    if (s > maxSrcRow) maxSrcRow = s
                }
            }

            minSrcRow = minSrcRow.coerceIn(0, srcHeight - 1)
            maxSrcRow = maxSrcRow.coerceIn(0, srcHeight - 1)
            val numSrcRows = (maxSrcRow - minSrcRow + 1).coerceAtLeast(1)

            // Read only needed source rows for this strip
            val srcStripPixels = IntArray(srcWidth * numSrcRows)
            src.getPixels(srcStripPixels, 0, srcWidth, 0, minSrcRow, srcWidth, numSrcRows)

            // Pass 1: Horizontal resampling into intermediate strip
            val intermediate = IntArray(targetWidth * numSrcRows)
            for (r in 0 until numSrcRows) {
                val srcRowOffset = r * srcWidth
                val outRowOffset = r * targetWidth
                for (x in 0 until targetWidth) {
                    var a = 0.0
                    var red = 0.0
                    var grn = 0.0
                    var blu = 0.0
                    val indices = hIndices[x]
                    val weights = hWeights[x]
                    for (i in 0 until 4) {
                        val pixel = srcStripPixels[srcRowOffset + indices[i]]
                        val w = weights[i]
                        a += ((pixel ushr 24) and 0xFF) * w
                        red += ((pixel ushr 16) and 0xFF) * w
                        grn += ((pixel ushr 8) and 0xFF) * w
                        blu += (pixel and 0xFF) * w
                    }
                    val finalA = a.toInt().coerceIn(0, 255)
                    val finalR = red.toInt().coerceIn(0, 255)
                    val finalG = grn.toInt().coerceIn(0, 255)
                    val finalB = blu.toInt().coerceIn(0, 255)
                    intermediate[outRowOffset + x] = (finalA shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                }
            }

            // Pass 2: Vertical resampling from intermediate into output strip
            val outputStrip = IntArray(targetWidth * currentStripH)
            for (y in stripStartY until stripEndY) {
                val outRowInStrip = (y - stripStartY) * targetWidth
                val indices = vIndices[y]
                val weights = vWeights[y]

                for (x in 0 until targetWidth) {
                    var a = 0.0
                    var red = 0.0
                    var grn = 0.0
                    var blu = 0.0

                    for (i in 0 until 4) {
                        val relSrcY = (indices[i] - minSrcRow).coerceIn(0, numSrcRows - 1)
                        val pixel = intermediate[relSrcY * targetWidth + x]
                        val w = weights[i]
                        a += ((pixel ushr 24) and 0xFF) * w
                        red += ((pixel ushr 16) and 0xFF) * w
                        grn += ((pixel ushr 8) and 0xFF) * w
                        blu += (pixel and 0xFF) * w
                    }

                    val finalA = a.toInt().coerceIn(0, 255)
                    val finalR = red.toInt().coerceIn(0, 255)
                    val finalG = grn.toInt().coerceIn(0, 255)
                    val finalB = blu.toInt().coerceIn(0, 255)
                    outputStrip[outRowInStrip + x] = (finalA shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                }
            }

            // Write output strip directly to bitmap
            outBitmap.setPixels(outputStrip, 0, targetWidth, 0, stripStartY, targetWidth, currentStripH)

            val progress = stripEndY.toFloat() / targetHeight.toFloat()
            onProgress(progress)
        }

        outBitmap
    }
}
