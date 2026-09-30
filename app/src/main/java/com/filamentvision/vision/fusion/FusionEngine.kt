package com.filamentvision.vision.fusion

import com.filamentvision.model.FusionProfile
import com.filamentvision.model.MeasurementStatus
import com.filamentvision.model.VisionMeasurement
import com.filamentvision.vision.detection.CameraMeasurement
import kotlin.math.abs

enum class FusionReason {
    BOTH_CAMERAS,
    CAMERA_A_MISSING,
    CAMERA_B_MISSING,
    CAMERA_A_LOW_CONFIDENCE,
    CAMERA_B_LOW_CONFIDENCE,
    TIMESTAMP_MISMATCH,
}

data class FusionResult(
    val measurement: VisionMeasurement,
    val activeCameraIds: Set<String>,
    val cameraDifferenceMm: Double,
    val timestampsCompatible: Boolean,
    val reason: FusionReason,
)

class FusionEngine {
    fun fuse(cameraA: CameraMeasurement?, cameraB: CameraMeasurement?, profile: FusionProfile): VisionMeasurement? =
        fuseDetailed(cameraA, cameraB, profile)?.measurement

    fun fuseDetailed(
        cameraA: CameraMeasurement?,
        cameraB: CameraMeasurement?,
        profile: FusionProfile,
    ): FusionResult? {
        var validA = cameraA?.takeIf { it.valid && it.confidence >= profile.minimumConfidence }
        var validB = cameraB?.takeIf { it.valid && it.confidence >= profile.minimumConfidence }
        val timestampsCompatible = cameraA == null || cameraB == null ||
            abs(cameraA.timestamp - cameraB.timestamp) <= profile.maximumTimestampDifferenceMillis
        var reason = when {
            validA == null && cameraA != null -> FusionReason.CAMERA_A_LOW_CONFIDENCE
            validB == null && cameraB != null -> FusionReason.CAMERA_B_LOW_CONFIDENCE
            cameraA == null -> FusionReason.CAMERA_A_MISSING
            cameraB == null -> FusionReason.CAMERA_B_MISSING
            else -> FusionReason.BOTH_CAMERAS
        }
        if (validA != null && validB != null && !timestampsCompatible) {
            reason = FusionReason.TIMESTAMP_MISMATCH
            if (validA.timestamp >= validB.timestamp) validB = null else validA = null
        }
        if (validA == null && validB == null) return null
        val fused = when {
            validA != null && validB != null -> {
                val weightA = profile.cameraAWeight.coerceAtLeast(0.0)
                val weightB = profile.cameraBWeight.coerceAtLeast(0.0)
                val total = weightA + weightB
                if (total == 0.0) (validA.diameterMm + validB.diameterMm) / 2.0
                else (validA.diameterMm * weightA + validB.diameterMm * weightB) / total
            }
            validA != null -> validA.diameterMm
            else -> validB!!.diameterMm
        }
        val diameterA = validA?.diameterMm ?: Double.NaN
        val diameterB = validB?.diameterMm ?: Double.NaN
        val confidence = listOfNotNull(validA?.confidence, validB?.confidence).average()
        val measurement = VisionMeasurement(
            diameterA = diameterA,
            diameterB = diameterB,
            fusedDiameter = fused,
            shapeDifference = if (validA != null && validB != null) abs(diameterA - diameterB) else 0.0,
            cameraAConfidence = validA?.confidence ?: 0.0,
            cameraBConfidence = validB?.confidence ?: 0.0,
            confidence = confidence,
            status = when {
                validA == null -> MeasurementStatus.CAMERA_A_FAILURE
                validB == null -> MeasurementStatus.CAMERA_B_FAILURE
                else -> MeasurementStatus.NORMAL
            },
            timestamp = maxOf(validA?.timestamp ?: Long.MIN_VALUE, validB?.timestamp ?: Long.MIN_VALUE),
        )
        return FusionResult(
            measurement = measurement,
            activeCameraIds = setOfNotNull(validA?.cameraId, validB?.cameraId),
            cameraDifferenceMm = if (cameraA != null && cameraB != null) {
                abs(cameraA.diameterMm - cameraB.diameterMm)
            } else {
                Double.NaN
            },
            timestampsCompatible = timestampsCompatible,
            reason = reason,
        )
    }
}
