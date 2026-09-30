package com.filamentvision.input.decode

import com.filamentvision.input.frame.EncodedCameraFrame
import com.filamentvision.model.ImageEncoding
import com.filamentvision.model.ImageFormatProfile
import com.filamentvision.model.PixelFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawImageDecoderTest {
    @Test
    fun decodesGray8IntoArgbPixels() {
        val result = RawImageDecoder().decode(
            EncodedCameraFrame("A", 99L, byteArrayOf(0, 0xFF.toByte())),
            ImageFormatProfile(ImageEncoding.GRAYSCALE_RAW, 2, 1, PixelFormat.GRAY8),
        )

        assertTrue(result is ImageDecodeResult.Success)
        val frame = (result as ImageDecodeResult.Success).frame
        assertEquals(0xFF000000.toInt(), frame.pixels[0])
        assertEquals(0xFFFFFFFF.toInt(), frame.pixels[1])
        assertEquals(99L, frame.timestampMillis)
    }

    @Test
    fun rejectsPayloadSmallerThanConfiguredDimensions() {
        val result = RawImageDecoder().decode(
            EncodedCameraFrame("A", 0L, byteArrayOf(1)),
            ImageFormatProfile(ImageEncoding.RGB_RAW, 2, 2, PixelFormat.RGB24),
        )

        assertTrue(result is ImageDecodeResult.Error)
    }

    @Test
    fun leavesGeometryCanonicalForTheSharedTransformer() {
        val result = RawImageDecoder().decode(
            EncodedCameraFrame("A", 7L, byteArrayOf(1, 2, 3, 4, 5, 6)),
            ImageFormatProfile(
                encoding = ImageEncoding.GRAYSCALE_RAW,
                width = 2,
                height = 3,
                pixelFormat = PixelFormat.GRAY8,
                rotationDegrees = 90,
                mirrorHorizontally = true,
                mirrorVertically = true,
            ),
        ) as ImageDecodeResult.Success

        assertEquals(2, result.frame.width)
        assertEquals(3, result.frame.height)
        assertEquals(0xFF010101.toInt(), result.frame.pixels.first())
        assertEquals(0xFF060606.toInt(), result.frame.pixels.last())
    }
}
