package com.filamentvision.vision.preprocessing

import com.filamentvision.camera.model.ArgbFrame
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageProcessorsTest {
    @Test
    fun grayscalePreservesDimensionsAndMapsKnownLuminanceWithoutMutatingInput() {
        val pixels = intArrayOf(
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFF0000.toInt(),
            0xFF00FF00.toInt(),
        )
        val input = ArgbFrame("A", 10L, 2, 2, pixels.copyOf())

        val first = GrayscaleProcessor().process(input)
        val second = GrayscaleProcessor().process(input)

        assertEquals(2, first.width)
        assertEquals(2, first.height)
        assertArrayEquals(intArrayOf(0, 255, 76, 150), first.intensities)
        assertArrayEquals(first.intensities, second.intensities)
        assertArrayEquals(pixels, input.pixels)
    }

    @Test
    fun thresholdUsesGreaterThanOrEqualBoundaryAndPreservesInput() {
        val inputValues = intArrayOf(0, 127, 128, 255)
        val input = IntensityFrame("A", 20L, 2, 2, inputValues.copyOf())

        val middle = ThresholdProcessor().process(input, imageThreshold = 128)
        val zero = ThresholdProcessor().process(input, imageThreshold = 0)
        val maximum = ThresholdProcessor().process(input, imageThreshold = 255)

        assertArrayEquals(intArrayOf(0, 0, 255, 255), middle.intensities)
        assertArrayEquals(intArrayOf(255, 255, 255, 255), zero.intensities)
        assertArrayEquals(intArrayOf(0, 0, 0, 255), maximum.intensities)
        assertEquals(2, middle.width)
        assertEquals(2, middle.height)
        assertArrayEquals(inputValues, input.intensities)
    }

    @Test
    fun horizontalEdgeMapFindsSharpVerticalBoundaryAndIgnoresUniformImage() {
        val uniform = IntensityFrame("A", 30L, 4, 2, IntArray(8) { 80 })
        val boundary = IntensityFrame(
            "A",
            40L,
            4,
            2,
            intArrayOf(10, 10, 240, 240, 10, 10, 240, 240),
        )

        val uniformEdges = EdgeMapProcessor().process(uniform)
        val first = EdgeMapProcessor().process(boundary)
        val second = EdgeMapProcessor().process(boundary)

        assertArrayEquals(IntArray(8), uniformEdges.intensities)
        assertArrayEquals(intArrayOf(0, 230, 230, 0, 0, 230, 230, 0), first.intensities)
        assertArrayEquals(first.intensities, second.intensities)
        assertEquals(4, first.width)
        assertEquals(2, first.height)
    }
}
