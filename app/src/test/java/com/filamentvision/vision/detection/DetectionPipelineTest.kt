package com.filamentvision.vision.detection

import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.model.CalibrationProfile
import com.filamentvision.model.ThresholdMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionPipelineTest {
    @Test
    fun measuresRealDarkFilamentPixelsAndConvertsToMillimetres() {
        val frame = verticalFilamentFrame(width = 20, height = 10, left = 7, rightExclusive = 12)
        val calibration = calibration(ThresholdMode.MANUAL)

        val result = DetectionPipeline().process(frame, calibration)

        assertTrue(result.valid)
        assertEquals(5.0, result.pixelWidth, 0.01)
        assertEquals(0.5, result.diameterMm, 0.001)
        assertTrue(result.confidence > 0.9)
        assertEquals("A", result.cameraId)
    }

    @Test
    fun automaticThresholdFindsTheSameEdges() {
        val frame = verticalFilamentFrame(width = 20, height = 10, left = 6, rightExclusive = 14)

        val result = DetectionPipeline().process(frame, calibration(ThresholdMode.AUTOMATIC))

        assertTrue(result.valid)
        assertEquals(8.0, result.pixelWidth, 0.01)
    }

    @Test
    fun rejectsWidthOutsideCalibrationRange() {
        val frame = verticalFilamentFrame(width = 20, height = 10, left = 2, rightExclusive = 18)

        val result = DetectionPipeline().process(frame, calibration(ThresholdMode.MANUAL))

        assertTrue(!result.valid)
        assertEquals(DetectionError.WIDTH_OUT_OF_RANGE, result.error)
    }

    private fun calibration(mode: ThresholdMode) = CalibrationProfile(
        roiLeft = 0,
        roiTop = 0,
        roiRight = 20,
        roiBottom = 10,
        thresholdMode = mode,
        manualThreshold = 128,
        edgeSensitivity = 20,
        minimumPixelWidth = 3,
        maximumPixelWidth = 12,
        mmPerPixel = 0.1,
        minimumConfidence = 0.7,
    )

    private fun verticalFilamentFrame(width: Int, height: Int, left: Int, rightExclusive: Int): ArgbFrame {
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }
        repeat(height) { y ->
            for (x in left until rightExclusive) pixels[y * width + x] = 0xFF101010.toInt()
        }
        return ArgbFrame("A", 123L, width, height, pixels)
    }
}

