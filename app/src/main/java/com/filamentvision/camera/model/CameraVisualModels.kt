package com.filamentvision.camera.model

data class FilamentVisualGeometry(
    val roiLeft: Int,
    val roiTop: Int,
    val roiRight: Int,
    val roiBottom: Int,
    val leftEdge: Int,
    val rightEdge: Int,
    val confidence: Double,
) {
    val pixelWidth: Int get() = rightEdge - leftEdge
}

data class ArgbFrame(
    val sourceId: String,
    val timestampMillis: Long,
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val sequence: Long = 0L,
    val geometry: FilamentVisualGeometry? = null,
)

enum class CameraVisualSourceState {
    STOPPED,
    WAITING_FOR_DEVICE,
    STARTING,
    LIVE,
    ERROR,
}
