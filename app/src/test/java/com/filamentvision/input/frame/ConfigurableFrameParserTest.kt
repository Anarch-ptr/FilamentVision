package com.filamentvision.input.frame

import com.filamentvision.model.FrameBoundaryMode
import com.filamentvision.model.FrameParsingProfile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurableFrameParserTest {
    @Test
    fun lengthFrameMayArriveInTwoChunks() {
        val parser = ConfigurableFrameParser(
            profile = FrameParsingProfile(
                boundaryMode = FrameBoundaryMode.LENGTH_FIELD,
                lengthFieldOffset = 0,
                lengthFieldSizeBytes = 2,
                cameraIdentifierOffset = 2,
                cameraIdentifierSize = 1,
                payloadOffset = 3,
                maximumFrameBytes = 100,
            ),
            cameraIdentifiers = mapOf("A" to byteArrayOf(0x41)),
        )

        assertTrue(parser.accept(byteArrayOf(0, 4, 0x41)).frames.isEmpty())
        val result = parser.accept(byteArrayOf(10, 11, 12))

        assertEquals("A", result.frames.single().cameraId)
        assertArrayEquals(byteArrayOf(10, 11, 12), result.frames.single().payload)
    }

    @Test
    fun oneChunkMayContainMultipleHeaderFooterFrames() {
        val parser = ConfigurableFrameParser(
            profile = FrameParsingProfile(
                boundaryMode = FrameBoundaryMode.HEADER_FOOTER,
                headerHex = "AABB",
                footerHex = "EEFF",
                cameraIdentifierOffset = 2,
                cameraIdentifierSize = 1,
                payloadOffset = 3,
                maximumFrameBytes = 100,
            ),
            cameraIdentifiers = mapOf("A" to byteArrayOf(1), "B" to byteArrayOf(2)),
        )
        val bytes = byteArrayOf(
            0xAA.toByte(), 0xBB.toByte(), 1, 7, 0xEE.toByte(), 0xFF.toByte(),
            0xAA.toByte(), 0xBB.toByte(), 2, 8, 0xEE.toByte(), 0xFF.toByte(),
        )

        val result = parser.accept(bytes)

        assertEquals(listOf("A", "B"), result.frames.map { it.cameraId })
        assertArrayEquals(byteArrayOf(7), result.frames[0].payload)
        assertArrayEquals(byteArrayOf(8), result.frames[1].payload)
    }

    @Test
    fun headerMayBeSplitAcrossPackets() {
        val parser = markerParser()

        assertTrue(parser.accept(byteArrayOf(0x55, 0xAA.toByte())).frames.isEmpty())
        val result = parser.accept(byteArrayOf(0xBB.toByte(), 1, 9, 0xEE.toByte(), 0xFF.toByte()))

        assertEquals("A", result.frames.single().cameraId)
        assertArrayEquals(byteArrayOf(9), result.frames.single().payload)
    }

    @Test
    fun missingFooterIsDiscardedAndNextValidFrameIsRecovered() {
        val parser = markerParser()
        val result = parser.accept(byteArrayOf(
            0xAA.toByte(), 0xBB.toByte(), 1, 7, 0x00,
            0xAA.toByte(), 0xBB.toByte(), 1, 8, 0xEE.toByte(), 0xFF.toByte(),
        ))

        assertTrue(result.errors.isNotEmpty())
        assertEquals(1, result.frames.size)
        assertArrayEquals(byteArrayOf(8), result.frames.single().payload)
    }

    @Test
    fun invalidLengthIsRecoverableAndDoesNotAllocateHugeFrame() {
        val parser = ConfigurableFrameParser(
            FrameParsingProfile(
                boundaryMode = FrameBoundaryMode.LENGTH_FIELD,
                lengthFieldSizeBytes = 4,
                payloadOffset = 4,
                maximumFrameBytes = 16,
            ),
            emptyMap(),
        )

        val result = parser.accept(byteArrayOf(0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))

        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.frames.isEmpty())
        assertTrue(parser.bufferedByteCount <= 16)
    }

    private fun markerParser() = ConfigurableFrameParser(
        profile = FrameParsingProfile(
            boundaryMode = FrameBoundaryMode.HEADER_FOOTER,
            headerHex = "AABB",
            footerHex = "EEFF",
            cameraIdentifierOffset = 2,
            cameraIdentifierSize = 1,
            payloadOffset = 3,
            maximumFrameBytes = 100,
        ),
        cameraIdentifiers = mapOf("A" to byteArrayOf(1)),
    )
}
