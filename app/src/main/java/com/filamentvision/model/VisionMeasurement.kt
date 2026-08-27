package com.filamentvision.model

/**
 * Small immutable output from the vision layer. Raw values from both camera
 * views are retained so fusion and shape analysis can evolve independently.
 */
data class VisionMeasurement(
    val diameterA: Double,
    val diameterB: Double,
    val fusedDiameter: Double,
    val shapeDifference: Double,
    val cameraAConfidence: Double,
    val cameraBConfidence: Double,
    val confidence: Double,
    val status: MeasurementStatus,
    val timestamp: Long,
)

