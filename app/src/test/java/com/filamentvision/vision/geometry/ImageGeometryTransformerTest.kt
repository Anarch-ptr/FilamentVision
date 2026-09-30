package com.filamentvision.vision.geometry

import com.filamentvision.camera.model.ArgbFrame
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageGeometryTransformerTest {
    @Test
    fun rotationsMapPixelsAndDimensionsClockwise() {
        val source = frame(width = 2, height = 3, pixels = intArrayOf(1, 2, 3, 4, 5, 6))

        val rotation0 = ImageGeometryTransformer.transform(source, 0, false, false)
        val rotation90 = ImageGeometryTransformer.transform(source, 90, false, false)
        val rotation180 = ImageGeometryTransformer.transform(source, 180, false, false)
        val rotation270 = ImageGeometryTransformer.transform(source, 270, false, false)

        assertEquals(2, rotation0.frame.width)
        assertEquals(3, rotation0.frame.height)
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 5, 6), rotation0.frame.pixels)
        assertEquals(3, rotation90.frame.width)
        assertEquals(2, rotation90.frame.height)
        assertArrayEquals(intArrayOf(5, 3, 1, 6, 4, 2), rotation90.frame.pixels)
        assertArrayEquals(intArrayOf(6, 5, 4, 3, 2, 1), rotation180.frame.pixels)
        assertEquals(3, rotation270.frame.width)
        assertEquals(2, rotation270.frame.height)
        assertArrayEquals(intArrayOf(2, 4, 6, 1, 3, 5), rotation270.frame.pixels)
    }

    @Test
    fun horizontalVerticalAndCombinedMirrorsUseTheSameMappingAsPixels() {
        val source = frame(width = 3, height = 2, pixels = intArrayOf(1, 2, 3, 4, 5, 6))

        val horizontal = ImageGeometryTransformer.transform(source, 0, true, false)
        val vertical = ImageGeometryTransformer.transform(source, 0, false, true)
        val combined = ImageGeometryTransformer.transform(source, 0, true, true)

        assertArrayEquals(intArrayOf(3, 2, 1, 6, 5, 4), horizontal.frame.pixels)
        assertArrayEquals(intArrayOf(4, 5, 6, 1, 2, 3), vertical.frame.pixels)
        assertArrayEquals(intArrayOf(6, 5, 4, 3, 2, 1), combined.frame.pixels)
        assertEquals(IntPoint(1, 1), vertical.mapping.mapPoint(IntPoint(1, 0)))
        assertEquals(IntRect(0, 0, 3, 1), vertical.mapping.mapRect(IntRect(0, 1, 3, 2)))
    }

    @Test
    fun rotationAndMirrorsMapPointsAndHalfOpenRectanglesIntoTransformedCoordinates() {
        val result = ImageGeometryTransformer.transform(frame(4, 3), 90, true, true)

        assertEquals(IntPoint(2, 0), result.mapping.mapPoint(IntPoint(3, 2)))
        assertEquals(IntPoint(0, 3), result.mapping.mapPoint(IntPoint(0, 0)))
        assertEquals(IntRect(1, 0, 3, 2), result.mapping.mapRect(IntRect(2, 1, 4, 3)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedRotation() {
        ImageGeometryTransformer.transform(frame(1, 1), 45, false, false)
    }

    private fun frame(width: Int, height: Int, pixels: IntArray = IntArray(width * height) { it + 1 }) =
        ArgbFrame(
            sourceId = "A",
            timestampMillis = 123L,
            width = width,
            height = height,
            pixels = pixels,
            sequence = 7L,
        )
}
