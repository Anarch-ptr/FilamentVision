package com.filamentvision.vision.processing

import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.model.CalibrationProfile
import com.filamentvision.model.CameraProfile
import com.filamentvision.model.GrayscaleMethod
import com.filamentvision.model.ImageFormatProfile
import com.filamentvision.model.ThresholdMode
import com.filamentvision.vision.detection.DetectionError
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionProcessorTest {
    @Test
    fun onePassExposesTheExactStagesUsedForMeasurement() {
        val result = VisionProcessor().process(fixedFilamentFrame(), calibratedCamera())

        assertEquals(40, result.effectiveThreshold)
        assertArrayEquals(grayArgbRows(), result.grayscalePreview.pixels)
        assertArrayEquals(binaryArgbRows(), result.thresholdPreview.pixels)
        assertArrayEquals(edgeArgbRows(), result.edgePreview.pixels)
        assertEquals(4.0, result.measurement!!.pixelWidth, 0.0)
        assertEquals(result.measurement.leftEdge, result.overlay.leftEdge)
        assertEquals(result.measurement.rightEdge, result.overlay.rightEdge)
        assertNull(result.error)
        assertTrue(result.timings.totalNanos >= result.timings.decodeNanos)
    }

    @Test
    fun automaticThresholdReportsTheEffectiveOtsuValue() {
        val result = VisionProcessor().process(
            fixedFilamentFrame(background = 200, filament = 20),
            calibratedCamera(thresholdMode = ThresholdMode.AUTOMATIC),
        )

        assertEquals(21, result.effectiveThreshold)
        assertEquals(4.0, result.measurement!!.pixelWidth, 0.0)
    }

    @Test
    fun failedDetectionKeepsRoiButNeverPublishesStaleEdges() {
        val white = ArgbFrame("A", 99L, 8, 4, IntArray(32) { argb(100) })

        val result = VisionProcessor().process(white, calibratedCamera())

        assertNull(result.measurement)
        assertEquals(DetectionError.NO_EDGES, result.error)
        assertEquals(0, result.overlay.roiLeft)
        assertEquals(8, result.overlay.roiRight)
        assertNull(result.overlay.leftEdge)
        assertNull(result.overlay.rightEdge)
    }

    @Test
    fun configuredGeometryIsAppliedBeforeEveryProcessingStage() {
        val camera = calibratedCamera().copy(
            imageFormat = ImageFormatProfile(rotationDegrees = 90, mirrorVertically = true),
            calibration = calibratedCamera().calibration.copy(
                roiRight = 4,
                roiBottom = 8,
                minimumPixelWidth = 1,
                maximumPixelWidth = 4,
            ),
        )

        val result = VisionProcessor().process(fixedFilamentFrame(), camera)

        assertEquals(4, result.rawPreview.width)
        assertEquals(8, result.rawPreview.height)
        assertEquals(result.rawPreview.width, result.grayscalePreview.width)
        assertEquals(result.rawPreview.height, result.edgePreview.height)
    }

    private fun calibratedCamera(thresholdMode: ThresholdMode = ThresholdMode.MANUAL) = CameraProfile(
        cameraId = "A",
        endpointId = "device",
        calibration = CalibrationProfile(
            roiLeft = 0,
            roiTop = 0,
            roiRight = 8,
            roiBottom = 4,
            grayscaleMethod = GrayscaleMethod.AVERAGE,
            thresholdMode = thresholdMode,
            manualThreshold = 40,
            edgeSensitivity = 30,
            minimumPixelWidth = 3,
            maximumPixelWidth = 6,
            mmPerPixel = 0.1,
            minimumConfidence = 0.5,
        ),
    )

    private fun fixedFilamentFrame(background: Int = 100, filament: Int = 20): ArgbFrame {
        val pixels = IntArray(8 * 4) { index ->
            val x = index % 8
            argb(if (x in 2 until 6) filament else background)
        }
        return ArgbFrame("A", 99L, 8, 4, pixels, sequence = 3L)
    }

    private fun grayArgbRows() = repeatedRows(intArrayOf(100, 100, 20, 20, 20, 20, 100, 100))
    private fun binaryArgbRows() = repeatedRows(intArrayOf(255, 255, 0, 0, 0, 0, 255, 255))
    private fun edgeArgbRows() = repeatedRows(intArrayOf(0, 255, 255, 0, 0, 255, 255, 0))
    private fun repeatedRows(values: IntArray) = IntArray(values.size * 4) { argb(values[it % values.size]) }

    private companion object {
        fun argb(value: Int) = 0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
    }
}
