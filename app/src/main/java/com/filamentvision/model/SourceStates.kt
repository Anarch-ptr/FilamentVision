package com.filamentvision.model

enum class VisionSourceState {
    STOPPED,
    STARTING,
    LIVE,
    ERROR,
}

enum class ConnectionState {
    DISCONNECTED,
    SEARCHING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR,
}

enum class MonitoringState {
    IDLE,
    READY,
    STARTING,
    MONITORING,
    STOPPING,
    COMPLETED,
    INTERRUPTED,
}
