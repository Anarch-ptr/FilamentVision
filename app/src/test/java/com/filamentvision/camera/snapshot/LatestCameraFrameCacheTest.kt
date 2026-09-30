package com.filamentvision.camera.snapshot

import com.filamentvision.camera.model.ArgbFrame
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class LatestCameraFrameCacheTest {
    @Test
    fun cameraFramesRemainIsolatedAndReturnedAsCopies() = runTest {
        val cache = LatestCameraFrameCache()
        cache.update(frame("A", 0xFF112233.toInt()))
        cache.update(frame("B", 0xFF445566.toInt()))

        val cameraA = cache.getLatestFrame("A")!!
        val cameraB = cache.getLatestFrame("B")!!

        assertEquals(0xFF112233.toInt(), cameraA.pixels.single())
        assertEquals(0xFF445566.toInt(), cameraB.pixels.single())
        assertNotSame(cameraA.pixels, cache.getLatestFrame("A")!!.pixels)
        assertNull(cache.getLatestFrame("C"))
    }

    private fun frame(id: String, pixel: Int) = ArgbFrame(id, 1L, 1, 1, intArrayOf(pixel))
}

