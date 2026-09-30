package com.filamentvision.vision.runtime

data class VisionPipelineDiagnostics(
    val activeInputPipelineCount: Int = 0,
    val officialMeasurementProducerCount: Int = 0,
    val previewObserverCount: Int = 0,
    val processedFrameCount: Long = 0,
    val droppedFrameCount: Long = 0,
    val processingFps: Double = 0.0,
)
