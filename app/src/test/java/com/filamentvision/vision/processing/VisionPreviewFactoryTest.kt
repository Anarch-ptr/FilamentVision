package com.filamentvision.vision.processing

import com.filamentvision.camera.model.ArgbFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionPreviewFactoryTest {
    @Test
    fun capsTheLongEdgeAndPreservesLandscapeAspectRatio() {
        val frame = ArgbFrame("A", 0L, 1000, 500, IntArray(500_000) { it })

        val preview = VisionPreviewFactory(maxLongEdge = 480).fromArgb(frame)

        assertEquals(480, preview.width)
        assertEquals(240, preview.height)
        assertEquals(480 * 240, preview.pixels.size)
        assertTrue(preview.pixels.first() == frame.pixels.first())
    }

    @Test
    fun copiesSmallFramesSoPublishedPreviewCannotBeMutatedByWorkingBuffers() {
        val pixels = intArrayOf(1, 2, 3, 4)
        val frame = ArgbFrame("A", 0L, 2, 2, pixels)

        val preview = VisionPreviewFactory(maxLongEdge = 480).fromArgb(frame)
        pixels[0] = 99

        assertEquals(1, preview.pixels[0])
    }
}
