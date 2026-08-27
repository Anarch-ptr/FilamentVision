package com.filamentvision.ui.monitor

import com.filamentvision.model.CalibrationStatus
import com.filamentvision.model.CameraStatus
import com.filamentvision.model.VisionMeasurement
import com.filamentvision.model.ConnectionState
import com.filamentvision.model.ConnectionType
import com.filamentvision.model.ConnectionSettings
import com.filamentvision.model.MonitoringConfiguration
import com.filamentvision.model.MonitoringState
import com.filamentvision.model.SimulationScenario

data class MonitorUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val monitoringState: MonitoringState = MonitoringState.IDLE,
    val deviceName: String = "Filament Vision Sensor",
    val connectionType: ConnectionType = ConnectionType.WIFI,
    val connectionSettings: ConnectionSettings = ConnectionSettings(),
    val cameraAStatus: CameraStatus = CameraStatus.OFFLINE,
    val cameraBStatus: CameraStatus = CameraStatus.OFFLINE,
    val calibrationStatus: CalibrationStatus = CalibrationStatus.CALIBRATED,
    val configuration: MonitoringConfiguration = MonitoringConfiguration(),
    val selectedScenario: SimulationScenario = SimulationScenario.NORMAL,
    val activeFakeProducerCount: Int = 0,
) {
    val isConnected: Boolean get() = connectionState == ConnectionState.CONNECTED
    val isMonitoring: Boolean get() = monitoringState == MonitoringState.MONITORING
    val hasLivePreview: Boolean get() = isConnected && activeFakeProducerCount == 1
    val canStartMonitoring: Boolean
        get() = isConnected && monitoringState == MonitoringState.READY &&
            cameraAStatus != CameraStatus.ERROR && cameraBStatus != CameraStatus.ERROR
    val canDisconnect: Boolean get() = connectionState != ConnectionState.DISCONNECTED
    val canReconnect: Boolean
        get() = connectionState == ConnectionState.DISCONNECTED ||
            connectionState == ConnectionState.ERROR
}

data class LiveMeasurementUiState(
    val latestMeasurement: VisionMeasurement? = null,
    val bufferedSampleCount: Int = 0,
)

data class SessionClockUiState(val elapsedMillis: Long = 0L)

data class DiagnosticsUiState(
    val activeProducerCount: Int = 0,
    val totalProducerStarts: Int = 0,
    val emittedMeasurementCount: Long = 0,
    val realtimeBufferSize: Int = 0,
)
