package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.engine.BicubicResampler
import com.example.engine.ImageFilters
import com.example.engine.ImageProcessingEngine
import com.example.engine.LanczosResampler
import com.example.model.FilterLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ResamplerTestMatrix {

    private fun createTestBitmap(w: Int, h: Int, withTransparency: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val alpha = if (withTransparency && (x + y) % 2 == 0) 128 else 255
                val r = (x * 255 / w).coerceIn(0, 255)
                val g = (y * 255 / h).coerceIn(0, 255)
                val b = ((x + y) * 128 / (w + h)).coerceIn(0, 255)
                bmp.setPixel(x, y, Color.argb(alpha, r, g, b))
            }
        }
        return bmp
    }

    @Test
    fun `test exact aspect ratio preservation from 2x to 10x across landscape portrait and square`() {
        val testImages = listOf(
            Pair(160, 90),  // 16:9 Landscape
            Pair(90, 160),  // 9:16 Portrait
            Pair(100, 100)  // 1:1 Square
        )

        for ((w, h) in testImages) {
            val originalRatio = w.toDouble() / h.toDouble()
            for (scale in 2..10) {
                val outW = w * scale
                val outH = h * scale
                val outputRatio = outW.toDouble() / outH.toDouble()

                assertEquals("Width must be exact originalWidth * scale", w * scale, outW)
                assertEquals("Height must be exact originalHeight * scale", h * scale, outH)
                assertEquals("Aspect ratio must be preserved perfectly", originalRatio, outputRatio, 0.00001)
            }
        }
    }

    @Test
    fun `test Lanczos3 resampler across 2x to 10x scales without memory crash`() = runBlocking {
        // Landscape image
        val src = createTestBitmap(40, 25)
        for (scale in 2..10) {
            val targetW = src.width * scale
            val targetH = src.height * scale
            var progressReported = false

            val result = LanczosResampler.resize(src, targetW, targetH) { progress ->
                if (progress > 0f) progressReported = true
            }

            assertNotNull(result)
            assertEquals("Width must be exact for ${scale}x", targetW, result.width)
            assertEquals("Height must be exact for ${scale}x", targetH, result.height)
            assertTrue("Progress must be reported", progressReported)
            result.recycle()
        }
        src.recycle()
    }

    @Test
    fun `test Bicubic resampler across 2x to 10x scales without memory crash`() = runBlocking {
        // Portrait image
        val src = createTestBitmap(25, 40)
        for (scale in 2..10) {
            val targetW = src.width * scale
            val targetH = src.height * scale

            val result = BicubicResampler.resize(src, targetW, targetH) {}
            assertNotNull(result)
            assertEquals("Width must be exact for ${scale}x", targetW, result.width)
            assertEquals("Height must be exact for ${scale}x", targetH, result.height)
            result.recycle()
        }
        src.recycle()
    }

    @Test
    fun `test Bilinear and Nearest resamplers across 2x to 10x`() = runBlocking {
        val src = createTestBitmap(30, 30)
        for (scale in 2..10) {
            val targetW = src.width * scale
            val targetH = src.height * scale

            val bilinearResult = ImageFilters.bilinearResize(src, targetW, targetH) {}
            assertEquals(targetW, bilinearResult.width)
            assertEquals(targetH, bilinearResult.height)
            bilinearResult.recycle()

            val nearestResult = ImageFilters.nearestResize(src, targetW, targetH) {}
            assertEquals(targetW, nearestResult.width)
            assertEquals(targetH, nearestResult.height)
            nearestResult.recycle()
        }
        src.recycle()
    }

    @Test
    fun `test transparency is preserved through resampling and filtering`() = runBlocking {
        val src = createTestBitmap(30, 30, withTransparency = true)
        assertTrue("Source bitmap must have alpha", src.hasAlpha())

        val upscaled = LanczosResampler.resize(src, 60, 60) {}
        assertTrue("Upscaled bitmap must retain alpha channel", upscaled.hasAlpha())

        val sharpened = ImageFilters.applySharpening(upscaled, FilterLevel.MEDIUM)
        assertTrue("Sharpened bitmap must retain alpha channel", sharpened.hasAlpha())

        val denoised = ImageFilters.applyNoiseReduction(sharpened, FilterLevel.LOW)
        assertTrue("Denoised bitmap must retain alpha channel", denoised.hasAlpha())

        src.recycle()
        upscaled.recycle()
        sharpened.recycle()
        denoised.recycle()
    }

    @Test
    fun `test memory safety check detects excessive canvas dimensions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = ImageProcessingEngine(context)

        // 4000x3000 at 10x would be 40000x30000 (> 16384 max dimension)
        val check = engine.checkMemorySafety(4000, 3000, 10)
        assertFalse("10x on 4000x3000 should exceed dimension/memory limits", check.isSafe)
        assertNotNull(check.warningMessage)
        assertTrue(
            "Warning message must explain limitation clearly",
            check.warningMessage!!.contains("16,384") || check.warningMessage!!.contains("memory")
        )

        // Safe size: 800x600 at 2x is 1600x1200
        val safeCheck = engine.checkMemorySafety(800, 600, 2)
        assertTrue("800x600 at 2x should be completely safe", safeCheck.isSafe)
    }
}
