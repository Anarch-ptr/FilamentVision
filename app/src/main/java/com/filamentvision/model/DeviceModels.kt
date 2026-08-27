package com.filamentvision.model

enum class ConnectionType {
    BLUETOOTH,
    WIFI,
}

enum class CameraStatus {
    OFFLINE,
    READY,
    STARTING,
    LIVE,
    LOW_CONFIDENCE,
    ERROR,
}

enum class CalibrationStatus {
    NOT_CALIBRATED,
    CALIBRATING,
    CALIBRATED,
}

data class MonitoringConfiguration(
    val targetDiameterMm: Double = 1.750,
    val measurementRateHz: Int = 8,
    val realtimeBufferCapacity: Int = 300,
)
