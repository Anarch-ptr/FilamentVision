package com.filamentvision.vision.detection

import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.model.CalibrationProfile
import com.filamentvision.vision.preprocessing.EdgeMapProcessor
import com.filamentvision.vision.preprocessing.GrayscaleProcessor
import com.filamentvision.vision.preprocessing.IntensityFrame
import com.filamentvision.vision.preprocessing.ThresholdProcessor
import kotlin.math.abs
import kotlin.math.sqrt

enum class DetectionError { INVALID_CALIBRATION, INVALID_ROI, NO_EDGES, WIDTH_OUT_OF_RANGE, LOW_CONFIDENCE }

data class CameraMeasurement(
    val cameraId: String,
    val pixelWidth: Double,
    val diameterMm: Double,
    val confidence: Double,
    val timestamp: Long,
    val valid: Boolean,
    val roiLeft: Int = 0,
    val roiTop: Int = 0,
    val roiRight: Int = 0,
    val roiBottom: Int = 0,
    val leftEdge: Int? = null,
    val rightEdge: Int? = null,
    val error: DetectionError? = null,
)

class DetectionPipeline(
    private val grayscaleProcessor: GrayscaleProcessor = GrayscaleProcessor(),
    private val thresholdProcessor: ThresholdProcessor = ThresholdProcessor(),
    private val edgeMapProcessor: EdgeMapProcessor = EdgeMapProcessor(),
) {
    /** Compatibility entry point. Runtime code supplies the already computed stages to [detect]. */
    fun process(frame: ArgbFrame, calibration: CalibrationProfile): CameraMeasurement {
        val grayscale = grayscaleProcessor.process(frame, calibration.grayscaleMethod)
        val threshold = thresholdProcessor.process(grayscale, calibration)
        val edges = edgeMapProcessor.process(grayscale, threshold.frame, calibration.edgeSensitivity)
        return detect(grayscale, threshold.frame, edges, frame, calibration)
    }

    fun detect(
        grayscale: IntensityFrame,
        threshold: IntensityFrame,
        edges: IntensityFrame,
        frame: ArgbFrame,
        calibration: CalibrationProfile,
    ): CameraMeasurement {
        requireStagesMatch(frame, grayscale, threshold, edges)
        if (calibration.mmPerPixel <= 0.0 || calibration.minimumPixelWidth <= 0 ||
            calibration.minimumPixelWidth > calibration.maximumPixelWidth
        ) return invalid(frame, calibration, DetectionError.INVALID_CALIBRATION)
        if (calibration.roiLeft < 0 || calibration.roiTop < 0 ||
            calibration.roiRight > frame.width || calibration.roiBottom > frame.height ||
            calibration.roiRight <= calibration.roiLeft || calibration.roiBottom <= calibration.roiTop
        ) return invalid(frame, calibration, DetectionError.INVALID_ROI)

        val rows = mutableListOf<RowDetection>()
        var sawOutOfRangeSegment = false
        for (y in calibration.roiTop until calibration.roiBottom) {
            val row = detectRow(threshold, edges, y, calibration)
            if (row != null) rows += row
            else if (hasDarkSegment(threshold, y, calibration)) sawOutOfRangeSegment = true
        }
        if (rows.isEmpty()) {
            val error = if (sawOutOfRangeSegment) DetectionError.WIDTH_OUT_OF_RANGE else DetectionError.NO_EDGES
            return invalid(frame, calibration, error)
        }

        val widths = rows.map { it.width }.sorted()
        val width = median(widths)
        val mean = widths.average()
        val deviation = sqrt(widths.sumOf { (it - mean) * (it - mean) } / widths.size)
        val coverage = rows.size.toDouble() / (calibration.roiBottom - calibration.roiTop)
        val consistency = (1.0 - deviation / mean.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        val confidence = (coverage * consistency).coerceIn(0.0, 1.0)
        val left = median(rows.map { it.left.toDouble() }.sorted()).toInt()
        val right = median(rows.map { it.right.toDouble() }.sorted()).toInt()
        val valid = confidence >= calibration.minimumConfidence
        return CameraMeasurement(
            cameraId = frame.sourceId,
            pixelWidth = width,
            diameterMm = width * calibration.mmPerPixel,
            confidence = confidence,
            timestamp = frame.timestampMillis,
            valid = valid,
            roiLeft = calibration.roiLeft,
            roiTop = calibration.roiTop,
            roiRight = calibration.roiRight,
            roiBottom = calibration.roiBottom,
            leftEdge = left,
            rightEdge = right,
            error = if (valid) null else DetectionError.LOW_CONFIDENCE,
        )
    }

    private fun detectRow(
        threshold: IntensityFrame,
        edges: IntensityFrame,
        y: Int,
        calibration: CalibrationProfile,
    ): RowDetection? {
        val candidates = mutableListOf<RowDetection>()
        var x = calibration.roiLeft
        while (x < calibration.roiRight) {
            while (x < calibration.roiRight && threshold.intensities[y * threshold.width + x] != 0) x++
            val left = x
            while (x < calibration.roiRight && threshold.intensities[y * threshold.width + x] == 0) x++
            val right = x
            val runWidth = right - left
            if (runWidth in calibration.minimumPixelWidth..calibration.maximumPixelWidth &&
                hasBoundaryEdges(edges, y, left, right, calibration)
            ) candidates += RowDetection(left, right, runWidth.toDouble())
        }
        val roiCenter = (calibration.roiLeft + calibration.roiRight) / 2.0
        return candidates.minByOrNull { abs((it.left + it.right) / 2.0 - roiCenter) }
    }

    private fun hasBoundaryEdges(
        edges: IntensityFrame,
        y: Int,
        left: Int,
        right: Int,
        calibration: CalibrationProfile,
    ): Boolean {
        if (left <= calibration.roiLeft || right >= calibration.roiRight) return false
        val row = y * edges.width
        val hasLeft = edges.intensities[row + left] > 0 || edges.intensities[row + left - 1] > 0
        val hasRight = edges.intensities[row + right] > 0 || edges.intensities[row + right - 1] > 0
        return hasLeft && hasRight
    }

    private fun hasDarkSegment(threshold: IntensityFrame, y: Int, calibration: CalibrationProfile): Boolean =
        (calibration.roiLeft until calibration.roiRight).any { threshold.intensities[y * threshold.width + it] == 0 }

    private fun requireStagesMatch(frame: ArgbFrame, vararg stages: IntensityFrame) {
        stages.forEach { stage ->
            require(stage.width == frame.width && stage.height == frame.height)
            require(stage.intensities.size >= frame.pixels.size)
        }
    }

    private fun invalid(frame: ArgbFrame, calibration: CalibrationProfile, error: DetectionError) = CameraMeasurement(
        cameraId = frame.sourceId,
        pixelWidth = 0.0,
        diameterMm = 0.0,
        confidence = 0.0,
        timestamp = frame.timestampMillis,
        valid = false,
        roiLeft = calibration.roiLeft,
        roiTop = calibration.roiTop,
        roiRight = calibration.roiRight,
        roiBottom = calibration.roiBottom,
        error = error,
    )

    private fun median(values: List<Double>): Double = if (values.size % 2 == 1) values[values.size / 2]
    else (values[values.size / 2 - 1] + values[values.size / 2]) / 2.0

    private data class RowDetection(val left: Int, val right: Int, val width: Double)
}
