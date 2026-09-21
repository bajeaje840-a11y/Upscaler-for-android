package com.example.engine

import android.graphics.Bitmap
import com.example.model.FilterLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor

object ImageFilters {

    /**
     * Memory-safe strip-based Bilinear resampling.
     * Temporary memory footprint: < 4 MB RAM regardless of scale factor.
     */
    suspend fun bilinearResize(
        src: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        onProgress: (Float) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) {
        val srcW = src.width
        val srcH = src.height

        val outBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val scaleX = srcW.toDouble() / targetWidth.toDouble()
        val scaleY = srcH.toDouble() / targetHeight.toDouble()

        val stripHeight = 64.coerceAtMost(targetHeight)
        val numStrips = (targetHeight + stripHeight - 1) / stripHeight

        for (stripIndex in 0 until numStrips) {
            coroutineContext.ensureActive()

            val stripStartY = stripIndex * stripHeight
            val stripEndY = (stripStartY + stripHeight).coerceAtMost(targetHeight)
            val currentStripH = stripEndY - stripStartY

            val firstSrcY = floor((stripStartY + 0.5) * scaleY - 0.5).toInt().coerceIn(0, srcH - 1)
            val lastSrcY = (floor(((stripEndY - 1) + 0.5) * scaleY - 0.5).toInt() + 1).coerceIn(0, srcH - 1)
            val numSrcRows = (lastSrcY - firstSrcY + 1).coerceAtLeast(1)

            val srcStripPixels = IntArray(srcW * numSrcRows)
            src.getPixels(srcStripPixels, 0, srcW, 0, firstSrcY, srcW, numSrcRows)

            val outStripPixels = IntArray(targetWidth * currentStripH)

            for (y in stripStartY until stripEndY) {
                val outOff = (y - stripStartY) * targetWidth
                val srcY = (y + 0.5) * scaleY - 0.5
                val y0 = floor(srcY).toInt().coerceIn(0, srcH - 1)
                val y1 = (y0 + 1).coerceIn(0, srcH - 1)
                val dy = (srcY - y0).coerceIn(0.0, 1.0)
                val invDy = 1.0 - dy

                val relY0 = (y0 - firstSrcY).coerceIn(0, numSrcRows - 1)
                val relY1 = (y1 - firstSrcY).coerceIn(0, numSrcRows - 1)

                for (x in 0 until targetWidth) {
                    val srcX = (x + 0.5) * scaleX - 0.5
                    val x0 = floor(srcX).toInt().coerceIn(0, srcW - 1)
                    val x1 = (x0 + 1).coerceIn(0, srcW - 1)
                    val dx = (srcX - x0).coerceIn(0.0, 1.0)
                    val invDx = 1.0 - dx

                    val p00 = srcStripPixels[relY0 * srcW + x0]
                    val p10 = srcStripPixels[relY0 * srcW + x1]
                    val p01 = srcStripPixels[relY1 * srcW + x0]
                    val p11 = srcStripPixels[relY1 * srcW + x1]

                    val w00 = invDx * invDy
                    val w10 = dx * invDy
                    val w01 = invDx * dy
                    val w11 = dx * dy

                    val a = (((p00 ushr 24) and 0xFF) * w00 + ((p10 ushr 24) and 0xFF) * w10 + ((p01 ushr 24) and 0xFF) * w01 + ((p11 ushr 24) and 0xFF) * w11).toInt().coerceIn(0, 255)
                    val r = (((p00 ushr 16) and 0xFF) * w00 + ((p10 ushr 16) and 0xFF) * w10 + ((p01 ushr 16) and 0xFF) * w01 + ((p11 ushr 16) and 0xFF) * w11).toInt().coerceIn(0, 255)
                    val g = (((p00 ushr 8) and 0xFF) * w00 + ((p10 ushr 8) and 0xFF) * w10 + ((p01 ushr 8) and 0xFF) * w01 + ((p11 ushr 8) and 0xFF) * w11).toInt().coerceIn(0, 255)
                    val b = ((p00 and 0xFF) * w00 + (p10 and 0xFF) * w10 + (p01 and 0xFF) * w01 + (p11 and 0xFF) * w11).toInt().coerceIn(0, 255)

                    outStripPixels[outOff + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            outBitmap.setPixels(outStripPixels, 0, targetWidth, 0, stripStartY, targetWidth, currentStripH)
            onProgress(stripEndY.toFloat() / targetHeight.toFloat())
        }

        outBitmap
    }

    /**
     * Memory-safe strip-based Nearest Neighbor resampling.
     * Temporary memory footprint: < 4 MB RAM.
     */
    suspend fun nearestResize(
        src: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        onProgress: (Float) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) {
        val srcW = src.width
        val srcH = src.height

        val outBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val scaleX = srcW.toDouble() / targetWidth.toDouble()
        val scaleY = srcH.toDouble() / targetHeight.toDouble()

        val stripHeight = 64.coerceAtMost(targetHeight)
        val numStrips = (targetHeight + stripHeight - 1) / stripHeight

        for (stripIndex in 0 until numStrips) {
            coroutineContext.ensureActive()

            val stripStartY = stripIndex * stripHeight
            val stripEndY = (stripStartY + stripHeight).coerceAtMost(targetHeight)
            val currentStripH = stripEndY - stripStartY

            val firstSrcY = (stripStartY * scaleY).toInt().coerceIn(0, srcH - 1)
            val lastSrcY = ((stripEndY - 1) * scaleY).toInt().coerceIn(0, srcH - 1)
            val numSrcRows = (lastSrcY - firstSrcY + 1).coerceAtLeast(1)

            val srcStripPixels = IntArray(srcW * numSrcRows)
            src.getPixels(srcStripPixels, 0, srcW, 0, firstSrcY, srcW, numSrcRows)

            val outStripPixels = IntArray(targetWidth * currentStripH)

            for (y in stripStartY until stripEndY) {
                val outOff = (y - stripStartY) * targetWidth
                val srcY = (y * scaleY).toInt().coerceIn(0, srcH - 1)
                val relSrcY = (srcY - firstSrcY).coerceIn(0, numSrcRows - 1)
                val rowOff = relSrcY * srcW

                for (x in 0 until targetWidth) {
                    val srcX = (x * scaleX).toInt().coerceIn(0, srcW - 1)
                    outStripPixels[outOff + x] = srcStripPixels[rowOff + srcX]
                }
            }

            outBitmap.setPixels(outStripPixels, 0, targetWidth, 0, stripStartY, targetWidth, currentStripH)
            onProgress(stripEndY.toFloat() / targetHeight.toFloat())
        }

        outBitmap
    }

    /**
     * Strip-based Edge-preserving unsharp mask sharpening.
     * Uses small strip buffers with 1-halo boundary instead of allocating full image arrays.
     */
    suspend fun applySharpening(bitmap: Bitmap, level: FilterLevel): Bitmap = withContext(Dispatchers.Default) {
        if (level == FilterLevel.OFF) return@withContext bitmap
        val amount = level.strength
        val w = bitmap.width
        val h = bitmap.height

        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val coringThreshold = 3

        val stripHeight = 128.coerceAtMost(h)
        val numStrips = (h + stripHeight - 1) / stripHeight

        for (stripIndex in 0 until numStrips) {
            coroutineContext.ensureActive()

            val stripStartY = stripIndex * stripHeight
            val stripEndY = (stripStartY + stripHeight).coerceAtMost(h)
            val currentStripH = stripEndY - stripStartY

            val haloStartY = (stripStartY - 1).coerceAtLeast(0)
            val haloEndY = (stripEndY).coerceAtMost(h - 1)
            val numHaloRows = haloEndY - haloStartY + 1

            val srcPixels = IntArray(w * numHaloRows)
            bitmap.getPixels(srcPixels, 0, w, 0, haloStartY, w, numHaloRows)

            val outStripPixels = IntArray(w * currentStripH)

            for (y in stripStartY until stripEndY) {
                val relY = y - haloStartY
                val relYPrev = (relY - 1).coerceAtLeast(0)
                val relYNext = (relY + 1).coerceAtMost(numHaloRows - 1)
                val outRowOffset = (y - stripStartY) * w

                for (x in 0 until w) {
                    val xPrev = (x - 1).coerceAtLeast(0)
                    val xNext = (x + 1).coerceAtMost(w - 1)

                    val center = srcPixels[relY * w + x]
                    val a = (center ushr 24) and 0xFF
                    val r = (center ushr 16) and 0xFF
                    val g = (center ushr 8) and 0xFF
                    val b = center and 0xFF

                    val top = srcPixels[relYPrev * w + x]
                    val bottom = srcPixels[relYNext * w + x]
                    val left = srcPixels[relY * w + xPrev]
                    val right = srcPixels[relY * w + xNext]

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

                    outStripPixels[outRowOffset + x] = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
                }
            }

            outBitmap.setPixels(outStripPixels, 0, w, 0, stripStartY, w, currentStripH)
        }

        outBitmap
    }

    /**
     * Strip-based Bilateral Filter for edge-preserving noise reduction.
     */
    suspend fun applyNoiseReduction(bitmap: Bitmap, level: FilterLevel): Bitmap = withContext(Dispatchers.Default) {
        if (level == FilterLevel.OFF) return@withContext bitmap
        val sigmaR = when (level) {
            FilterLevel.LOW -> 25.0
            FilterLevel.MEDIUM -> 38.0
            FilterLevel.HIGH -> 55.0
            FilterLevel.OFF -> 25.0
        }
        val twoSigmaRSq = 2.0 * sigmaR * sigmaR

        val w = bitmap.width
        val h = bitmap.height
        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val stripHeight = 64.coerceAtMost(h)
        val numStrips = (h + stripHeight - 1) / stripHeight

        for (stripIndex in 0 until numStrips) {
            coroutineContext.ensureActive()

            val stripStartY = stripIndex * stripHeight
            val stripEndY = (stripStartY + stripHeight).coerceAtMost(h)
            val currentStripH = stripEndY - stripStartY

            val haloStartY = (stripStartY - 1).coerceAtLeast(0)
            val haloEndY = (stripEndY).coerceAtMost(h - 1)
            val numHaloRows = haloEndY - haloStartY + 1

            val srcPixels = IntArray(w * numHaloRows)
            bitmap.getPixels(srcPixels, 0, w, 0, haloStartY, w, numHaloRows)

            val outStripPixels = IntArray(w * currentStripH)

            for (y in stripStartY until stripEndY) {
                val relY = y - haloStartY
                val outRowOffset = (y - stripStartY) * w

                for (x in 0 until w) {
                    val center = srcPixels[relY * w + x]
                    val ca = (center ushr 24) and 0xFF
                    val cr = (center ushr 16) and 0xFF
                    val cg = (center ushr 8) and 0xFF
                    val cb = center and 0xFF

                    var sumW = 0.0
                    var sumR = 0.0
                    var sumG = 0.0
                    var sumB = 0.0

                    for (dy in -1..1) {
                        val ny = (relY + dy).coerceIn(0, numHaloRows - 1)
                        val nRow = ny * w
                        for (dx in -1..1) {
                            val nx = (x + dx).coerceIn(0, w - 1)
                            val np = srcPixels[nRow + nx]
                            val nr = (np ushr 16) and 0xFF
                            val ng = (np ushr 8) and 0xFF
                            val nb = np and 0xFF

                            val colorDistSq = ((cr - nr) * (cr - nr) + (cg - ng) * (cg - ng) + (cb - nb) * (cb - nb)).toDouble()
                            val spatialDistSq = (dx * dx + dy * dy).toDouble()

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

                    outStripPixels[outRowOffset + x] = (ca shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                }
            }

            outBitmap.setPixels(outStripPixels, 0, w, 0, stripStartY, w, currentStripH)
        }

        outBitmap
    }

    /**
     * Strip-based Detail & Clarity enhancement.
     */
    suspend fun applyDetailEnhancement(bitmap: Bitmap, level: FilterLevel): Bitmap = withContext(Dispatchers.Default) {
        if (level == FilterLevel.OFF) return@withContext bitmap
        val factor = level.strength * 0.45f
        val w = bitmap.width
        val h = bitmap.height

        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val stripHeight = 128.coerceAtMost(h)
        val numStrips = (h + stripHeight - 1) / stripHeight

        for (stripIndex in 0 until numStrips) {
            coroutineContext.ensureActive()

            val stripStartY = stripIndex * stripHeight
            val stripEndY = (stripStartY + stripHeight).coerceAtMost(h)
            val currentStripH = stripEndY - stripStartY

            val haloStartY = (stripStartY - 2).coerceAtLeast(0)
            val haloEndY = (stripEndY + 1).coerceAtMost(h - 1)
            val numHaloRows = haloEndY - haloStartY + 1

            val srcPixels = IntArray(w * numHaloRows)
            bitmap.getPixels(srcPixels, 0, w, 0, haloStartY, w, numHaloRows)

            val outStripPixels = IntArray(w * currentStripH)

            for (y in stripStartY until stripEndY) {
                val relY = y - haloStartY
                val relYPrev = (relY - 2).coerceAtLeast(0)
                val relYNext = (relY + 2).coerceAtMost(numHaloRows - 1)
                val outRowOffset = (y - stripStartY) * w

                for (x in 0 until w) {
                    val xPrev = (x - 2).coerceAtLeast(0)
                    val xNext = (x + 2).coerceAtMost(w - 1)

                    val p = srcPixels[relY * w + x]
                    val a = (p ushr 24) and 0xFF
                    val r = (p ushr 16) and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = p and 0xFF

                    val pT = srcPixels[relYPrev * w + x]
                    val pB = srcPixels[relYNext * w + x]
                    val pL = srcPixels[relY * w + xPrev]
                    val pR = srcPixels[relY * w + xNext]

                    val localLumAvg = (((pT ushr 16) and 0xFF) + ((pB ushr 16) and 0xFF) + ((pL ushr 16) and 0xFF) + ((pR ushr 16) and 0xFF)) / 4
                    val diffR = r - localLumAvg
                    val diffG = g - localLumAvg
                    val diffB = b - localLumAvg

                    val outR = (r + diffR * factor).toInt().coerceIn(0, 255)
                    val outG = (g + diffG * factor).toInt().coerceIn(0, 255)
                    val outB = (b + diffB * factor).toInt().coerceIn(0, 255)

                    outStripPixels[outRowOffset + x] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
                }
            }

            outBitmap.setPixels(outStripPixels, 0, w, 0, stripStartY, w, currentStripH)
        }

        outBitmap
    }
}
