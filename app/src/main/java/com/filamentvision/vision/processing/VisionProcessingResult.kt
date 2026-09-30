package com.filamentvision.vision.processing

import com.filamentvision.vision.detection.CameraMeasurement
import com.filamentvision.vision.detection.DetectionError

data class PreviewFrame(val width: Int, val height: Int, val pixels: IntArray)

data class DetectionOverlay(
    val roiLeft: Int,
    val roiTop: Int,
    val roiRight: Int,
    val roiBottom: Int,
    val leftEdge: Int?,
    val rightEdge: Int?,
)

data class VisionStageTimings(
    val decodeNanos: Long,
    val geometryNanos: Long,
    val grayscaleNanos: Long,
    val thresholdNanos: Long,
    val edgeNanos: Long,
    val detectionNanos: Long,
    val previewNanos: Long,
    val totalNanos: Long,
)

data class VisionProcessingResult(
    val cameraId: String,
    val timestampMillis: Long,
    val sequence: Long,
    val processedWidth: Int,
    val processedHeight: Int,
    val rawPreview: PreviewFrame,
    val grayscalePreview: PreviewFrame,
    val thresholdPreview: PreviewFrame,
    val edgePreview: PreviewFrame,
    val overlay: DetectionOverlay,
    val measurement: CameraMeasurement?,
    val effectiveThreshold: Int,
    val timings: VisionStageTimings,
    val error: DetectionError?,
)
