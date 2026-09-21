package com.example.engine

import android.graphics.Bitmap
import com.example.model.FilterLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import kotlin.math.exp

object ImageFilters {

    suspend fun bilinearResize(
        src: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        onProgress: (Float) -> Unit
    ): Bitmap = coroutineScope {
        val srcW = src.width
        val srcH = src.height
        val srcPixels = IntArray(srcW * srcH)
        src.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)

        val outPixels = IntArray(targetWidth * targetHeight)
        val scaleX = srcW.toDouble() / targetWidth.toDouble()
        val scaleY = srcH.toDouble() / targetHeight.toDouble()

        val chunkSize = kotlin.math.max(1, targetHeight / 4)
        val jobs = (0 until targetHeight step chunkSize).map { startY ->
            val endY = kotlin.math.min(targetHeight, startY + chunkSize)
            async(Dispatchers.Default) {
                for (y in startY until endY) {
                    val outOff = y * targetWidth
                    val srcY = (y + 0.5) * scaleY - 0.5
                    val y0 = kotlin.math.floor(srcY).toInt().coerceIn(0, srcH - 1)
                    val y1 = (y0 + 1).coerceIn(0, srcH - 1)
                    val dy = (srcY - y0).coerceIn(0.0, 1.0)
                    val invDy = 1.0 - dy

                    for (x in 0 until targetWidth) {
                        val srcX = (x + 0.5) * scaleX - 0.5
                        val x0 = kotlin.math.floor(srcX).toInt().coerceIn(0, srcW - 1)
                        val x1 = (x0 + 1).coerceIn(0, srcW - 1)
                        val dx = (srcX - x0).coerceIn(0.0, 1.0)
                        val invDx = 1.0 - dx

                        val p00 = srcPixels[y0 * srcW + x0]
                        val p10 = srcPixels[y0 * srcW + x1]
                        val p01 = srcPixels[y1 * srcW + x0]
                        val p11 = srcPixels[y1 * srcW + x1]

                        val w00 = invDx * invDy
                        val w10 = dx * invDy
                        val w01 = invDx * dy
                        val w11 = dx * dy

                        val a = (((p00 ushr 24) and 0xFF) * w00 + ((p10 ushr 24) and 0xFF) * w10 + ((p01 ushr 24) and 0xFF) * w01 + ((p11 ushr 24) and 0xFF) * w11).toInt().coerceIn(0, 255)
                        val r = (((p00 ushr 16) and 0xFF) * w00 + ((p10 ushr 16) and 0xFF) * w10 + ((p01 ushr 16) and 0xFF) * w01 + ((p11 ushr 16) and 0xFF) * w11).toInt().coerceIn(0, 255)
                        val g = (((p00 ushr 8) and 0xFF) * w00 + ((p10 ushr 8) and 0xFF) * w10 + ((p01 ushr 8) and 0xFF) * w01 + ((p11 ushr 8) and 0xFF) * w11).toInt().coerceIn(0, 255)
                        val b = ((p00 and 0xFF) * w00 + (p10 and 0xFF) * w10 + (p01 and 0xFF) * w01 + (p11 and 0xFF) * w11).toInt().coerceIn(0, 255)

                        outPixels[outOff + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
            }
        }
        jobs.awaitAll()
        onProgress(1.0f)
        Bitmap.createBitmap(outPixels, targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    }

    suspend fun nearestResize(
        src: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        onProgress: (Float) -> Unit
    ): Bitmap = coroutineScope {
        val srcW = src.width
        val srcH = src.height
        val srcPixels = IntArray(srcW * srcH)
        src.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)

        val outPixels = IntArray(targetWidth * targetHeight)
        val scaleX = srcW.toDouble() / targetWidth.toDouble()
        val scaleY = srcH.toDouble() / targetHeight.toDouble()

        val chunkSize = kotlin.math.max(1, targetHeight / 4)
        val jobs = (0 until targetHeight step chunkSize).map { startY ->
            val endY = kotlin.math.min(targetHeight, startY + chunkSize)
            async(Dispatchers.Default) {
                for (y in startY until endY) {
                    val outOff = y * targetWidth
                    val srcY = (y * scaleY).toInt().coerceIn(0, srcH - 1)
                    val rowOff = srcY * srcW
                    for (x in 0 until targetWidth) {
                        val srcX = (x * scaleX).toInt().coerceIn(0, srcW - 1)
                        outPixels[outOff + x] = srcPixels[rowOff + srcX]
                    }
                }
            }
        }
        jobs.awaitAll()
        onProgress(1.0f)
        Bitmap.createBitmap(outPixels, targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    }

    /**
     * Edge-preserving unsharp mask sharpening
     * Computes difference between original pixel and low-pass neighbor average.
     * Uses coring threshold so flat regions are left untouched (no noise amplification).
     */
    suspend fun applySharpening(bitmap: Bitmap, level: FilterLevel): Bitmap = coroutineScope {
        if (level == FilterLevel.OFF) return@coroutineScope bitmap
        val amount = level.strength
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val output = IntArray(w * h)

        val coringThreshold = 3 // Minimum difference before sharpening triggers (noise gate)
        val chunkSize = kotlin.math.max(1, h / 4)

        val jobs = (0 until h step chunkSize).map { startY ->
            val endY = kotlin.math.min(h, startY + chunkSize)
            async(Dispatchers.Default) {
                for (y in startY until endY) {
                    val yPrev = (y - 1).coerceAtLeast(0)
                    val yNext = (y + 1).coerceAtMost(h - 1)
                    val row = y * w

                    for (x in 0 until w) {
                        val xPrev = (x - 1).coerceAtLeast(0)
                        val xNext = (x + 1).coerceAtMost(w - 1)

                        val center = pixels[row + x]
                        val a = (center ushr 24) and 0xFF
                        val r = (center ushr 16) and 0xFF
                        val g = (center ushr 8) and 0xFF
                        val b = center and 0xFF

                        // Cross-filter 5-tap neighborhood average
                        val top = pixels[yPrev * w + x]
                        val bottom = pixels[yNext * w + x]
                        val left = pixels[row + xPrev]
                        val right = pixels[row + xNext]

                        fun sharpenChannel(c: Int, topC: Int, botC: Int, leftC: Int, rightC: Int): Int {
                            val avg = (topC + botC + leftC + rightC) / 4
                            val diff = c - avg
                            return if (abs(diff) < coringThreshold) {
                                c
                            } else {
                                (c + (diff * amount)).toInt().coerceIn(0, 255)
                            }
                        }

                        val newR = sharpenChannel(r, (top ushr 16) and 0xFF, (bottom ushr 16) and 0xFF, (left ushr 16) and 0xFF, (right ushr 16) and 0xFF)
                        val newG = sharpenChannel(g, (top ushr 8) and 0xFF, (bottom ushr 8) and 0xFF, (left ushr 8) and 0xFF, (right ushr 8) and 0xFF)
                        val newB = sharpenChannel(b, top and 0xFF, bottom and 0xFF, left and 0xFF, right and 0xFF)

                        output[row + x] = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
                    }
                }
            }
        }
        jobs.awaitAll()
        Bitmap.createBitmap(output, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Edge-preserving Bilateral Filter for noise reduction
     * Averages neighboring pixels weighted by both spatial distance and color similarity,
     * protecting strong edges while smoothing flat noise.
     */
    suspend fun applyNoiseReduction(bitmap: Bitmap, level: FilterLevel): Bitmap = coroutineScope {
        if (level == FilterLevel.OFF) return@coroutineScope bitmap
        val sigmaR = when (level) {
            FilterLevel.LOW -> 25.0
            FilterLevel.MEDIUM -> 38.0
            FilterLevel.HIGH -> 55.0
            FilterLevel.OFF -> 25.0
        }
        val twoSigmaRSq = 2.0 * sigmaR * sigmaR

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val output = IntArray(w * h)

        val chunkSize = kotlin.math.max(1, h / 4)
        val jobs = (0 until h step chunkSize).map { startY ->
            val endY = kotlin.math.min(h, startY + chunkSize)
            async(Dispatchers.Default) {
                for (y in startY until endY) {
                    val row = y * w
                    for (x in 0 until w) {
                        val center = pixels[row + x]
                        val ca = (center ushr 24) and 0xFF
                        val cr = (center ushr 16) and 0xFF
                        val cg = (center ushr 8) and 0xFF
                        val cb = center and 0xFF

                        var sumW = 0.0
                        var sumR = 0.0
                        var sumG = 0.0
                        var sumB = 0.0

                        for (dy in -1..1) {
                            val ny = (y + dy).coerceIn(0, h - 1)
                            val nRow = ny * w
                            for (dx in -1..1) {
                                val nx = (x + dx).coerceIn(0, w - 1)
                                val np = pixels[nRow + nx]
                                val nr = (np ushr 16) and 0xFF
                                val ng = (np ushr 8) and 0xFF
                                val nb = np and 0xFF

                                val colorDistSq = ((cr - nr) * (cr - nr) + (cg - ng) * (cg - ng) + (cb - nb) * (cb - nb)).toDouble()
                                val spatialDistSq = (dx * dx + dy * dy).toDouble()

                                // Spatial weight (Gaussian sigmaS = 1.0) * Color weight (Bilateral)
                                val weight = exp(-spatialDistSq / 2.0) * exp(-colorDistSq / twoSigmaRSq)
                                sumW += weight
                                sumR += nr * weight
                                sumG += ng * weight
                                sumB += nb * weight
                            }
                        }

                        val finalR = (sumR / sumW).toInt().coerceIn(0, 255)
                        val finalG = (sumG / sumW).toInt().coerceIn(0, 255)
                        val finalB = (sumB / sumW).toInt().coerceIn(0, 255)

                        output[row + x] = (ca shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                    }
                }
            }
        }
        jobs.awaitAll()
        Bitmap.createBitmap(output, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Detail & Clarity enhancement:
     * Increases micro-contrast in midtones without blowing highlights or shadows.
     */
    suspend fun applyDetailEnhancement(bitmap: Bitmap, level: FilterLevel): Bitmap = coroutineScope {
        if (level == FilterLevel.OFF) return@coroutineScope bitmap
        val factor = level.strength * 0.45f
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val output = IntArray(w * h)

        val chunkSize = kotlin.math.max(1, h / 4)
        val jobs = (0 until h step chunkSize).map { startY ->
            val endY = kotlin.math.min(h, startY + chunkSize)
            async(Dispatchers.Default) {
                for (y in startY until endY) {
                    val yPrev = (y - 2).coerceAtLeast(0)
                    val yNext = (y + 2).coerceAtMost(h - 1)
                    val row = y * w
                    for (x in 0 until w) {
                        val xPrev = (x - 2).coerceAtLeast(0)
                        val xNext = (x + 2).coerceAtMost(w - 1)

                        val p = pixels[row + x]
                        val a = (p ushr 24) and 0xFF
                        val r = (p ushr 16) and 0xFF
                        val g = (p ushr 8) and 0xFF
                        val b = p and 0xFF

                        val pT = pixels[yPrev * w + x]
                        val pB = pixels[yNext * w + x]
                        val pL = pixels[row + xPrev]
                        val pR = pixels[row + xNext]

                        val localLumAvg = (((pT ushr 16) and 0xFF) + ((pB ushr 16) and 0xFF) + ((pL ushr 16) and 0xFF) + ((pR ushr 16) and 0xFF)) / 4
                        val diffR = r - localLumAvg
                        val diffG = g - localLumAvg
                        val diffB = b - localLumAvg

                        val outR = (r + diffR * factor).toInt().coerceIn(0, 255)
                        val outG = (g + diffG * factor).toInt().coerceIn(0, 255)
                        val outB = (b + diffB * factor).toInt().coerceIn(0, 255)

                        output[row + x] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
                    }
                }
            }
        }
        jobs.awaitAll()
        Bitmap.createBitmap(output, w, h, Bitmap.Config.ARGB_8888)
    }
}
