package com.filamentvision.vision.fusion

import com.filamentvision.model.FusionProfile
import com.filamentvision.vision.detection.CameraMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FusionEngineTest {
    private val profile = FusionProfile(0.25, 0.75, 0.7, 100)

    @Test
    fun fusesTwoValidMeasurementsUsingNormalizedWeights() {
        val result = FusionEngine().fuse(measurement("A", 1.7, 0.9, 1_000), measurement("B", 1.8, 0.8, 1_050), profile)

        assertEquals(1.7, result!!.diameterA, 0.0001)
        assertEquals(1.8, result.diameterB, 0.0001)
        assertEquals(1.775, result.fusedDiameter, 0.0001)
    }

    @Test
    fun usesOnlyValidCameraAndRejectsTwoInvalidCameras() {
        val a = measurement("A", 1.73, 0.9, 1_000)
        val invalidB = measurement("B", 1.90, 0.2, 1_000)

        val one = FusionEngine().fuse(a, invalidB, profile)

        assertEquals(1.73, one!!.fusedDiameter, 0.0001)
        assertTrue(one.diameterB.isNaN())
        assertNull(FusionEngine().fuse(invalidB.copy(cameraId = "A"), invalidB, profile))
    }

    @Test
    fun doesNotFuseMeasurementsOutsideTimestampTolerance() {
        val result = FusionEngine().fuse(
            measurement("A", 1.7, 0.9, 1_000),
            measurement("B", 1.8, 0.9, 1_500),
            profile,
        )

        assertEquals(1.8, result!!.fusedDiameter, 0.0001)
        assertTrue(result.diameterA.isNaN())
    }

    @Test
    fun detailedFallbackExplainsWhyOnlyCameraAWasUsed() {
        val result = FusionEngine().fuseDetailed(
            measurement("A", 1.73, 0.9, 1_000),
            measurement("B", 1.90, 0.2, 1_000),
            profile,
        )!!

        assertEquals(setOf("A"), result.activeCameraIds)
        assertEquals(FusionReason.CAMERA_B_LOW_CONFIDENCE, result.reason)
        assertEquals(0.17, result.cameraDifferenceMm, 0.0001)
        assertTrue(result.timestampsCompatible)
    }

    private fun measurement(id: String, diameter: Double, confidence: Double, time: Long) = CameraMeasurement(
        cameraId = id,
        pixelWidth = diameter * 100,
        diameterMm = diameter,
        confidence = confidence,
        timestamp = time,
        valid = true,
    )
}
