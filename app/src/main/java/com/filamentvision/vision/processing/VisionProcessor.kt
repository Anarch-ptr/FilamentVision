package com.filamentvision.vision.processing

import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.model.CameraProfile
import com.filamentvision.vision.detection.DetectionPipeline
import com.filamentvision.vision.geometry.ImageGeometryTransformer
import com.filamentvision.vision.preprocessing.EdgeMapProcessor
import com.filamentvision.vision.preprocessing.GrayscaleProcessor
import com.filamentvision.vision.preprocessing.ThresholdProcessor

class VisionProcessor(
    private val grayscaleProcessor: GrayscaleProcessor = GrayscaleProcessor(),
    private val thresholdProcessor: ThresholdProcessor = ThresholdProcessor(),
    private val edgeMapProcessor: EdgeMapProcessor = EdgeMapProcessor(),
    private val detectionPipeline: DetectionPipeline = DetectionPipeline(),
    private val previewFactory: VisionPreviewFactory = VisionPreviewFactory(),
) {
    fun process(frame: ArgbFrame, camera: CameraProfile, decodeNanos: Long = 0L): VisionProcessingResult {
        val totalStart = System.nanoTime()

        val geometryStart = System.nanoTime()
        val transformed = ImageGeometryTransformer.transform(frame, camera.imageFormat)
        val geometryNanos = System.nanoTime() - geometryStart

        val grayscaleStart = System.nanoTime()
        val grayscale = grayscaleProcessor.process(transformed.frame, camera.calibration.grayscaleMethod)
        val grayscaleNanos = System.nanoTime() - grayscaleStart

        val thresholdStart = System.nanoTime()
        val threshold = thresholdProcessor.process(grayscale, camera.calibration)
        val thresholdNanos = System.nanoTime() - thresholdStart

        val edgeStart = System.nanoTime()
        val edges = edgeMapProcessor.process(grayscale, threshold.frame, camera.calibration.edgeSensitivity)
        val edgeNanos = System.nanoTime() - edgeStart

        val detectionStart = System.nanoTime()
        val detection = detectionPipeline.detect(
            grayscale = grayscale,
            threshold = threshold.frame,
            edges = edges,
            frame = transformed.frame,
            calibration = camera.calibration,
        )
        val detectionNanos = System.nanoTime() - detectionStart

        val previewStart = System.nanoTime()
        val rawPreview = previewFactory.fromArgb(transformed.frame)
        val grayscalePreview = previewFactory.fromIntensity(grayscale)
        val thresholdPreview = previewFactory.fromIntensity(threshold.frame)
        val edgePreview = previewFactory.fromIntensity(edges)
        val previewNanos = System.nanoTime() - previewStart
        val totalNanos = decodeNanos + (System.nanoTime() - totalStart)

        return VisionProcessingResult(
            cameraId = transformed.frame.sourceId,
            timestampMillis = transformed.frame.timestampMillis,
            sequence = transformed.frame.sequence,
            processedWidth = transformed.frame.width,
            processedHeight = transformed.frame.height,
            rawPreview = rawPreview,
            grayscalePreview = grayscalePreview,
            thresholdPreview = thresholdPreview,
            edgePreview = edgePreview,
            overlay = DetectionOverlay(
                roiLeft = detection.roiLeft,
                roiTop = detection.roiTop,
                roiRight = detection.roiRight,
                roiBottom = detection.roiBottom,
                leftEdge = detection.leftEdge,
                rightEdge = detection.rightEdge,
            ),
            measurement = detection.takeIf { it.valid },
            effectiveThreshold = threshold.effectiveThreshold,
            timings = VisionStageTimings(
                decodeNanos = decodeNanos,
                geometryNanos = geometryNanos,
                grayscaleNanos = grayscaleNanos,
                thresholdNanos = thresholdNanos,
                edgeNanos = edgeNanos,
                detectionNanos = detectionNanos,
                previewNanos = previewNanos,
                totalNanos = totalNanos,
            ),
            error = detection.error,
        )
    }
}
