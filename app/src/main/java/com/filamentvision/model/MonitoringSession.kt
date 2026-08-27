package com.filamentvision.model

enum class SessionStatus {
    ACTIVE,
    COMPLETED,
    INTERRUPTED,
}

enum class MonitoringEventType {
    HIGH_DIAMETER,
    LOW_DIAMETER,
    CAMERA_MISMATCH,
    LOW_CONFIDENCE,
    CAMERA_A_FAILURE,
    CAMERA_B_FAILURE,
    CONNECTION_LOST,
}

data class MonitoringEvent(
    val timestamp: Long,
    val type: MonitoringEventType,
    val measurement: VisionMeasurement? = null,
    val description: String,
)

data class SessionStatistics(
    val sampleCount: Long = 0,
    val averageDiameter: Double = 0.0,
    val minimumDiameter: Double = 0.0,
    val maximumDiameter: Double = 0.0,
    val standardDeviation: Double = 0.0,
    val averageConfidence: Double = 0.0,
    val abnormalEventCount: Int = 0,
)

data class MonitoringSession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val targetDiameter: Double,
    val status: SessionStatus,
    val statistics: SessionStatistics,
    val events: List<MonitoringEvent>,
    val endReason: String? = null,
) {
    val durationMillis: Long get() = ((endedAt ?: startedAt) - startedAt).coerceAtLeast(0L)
}
