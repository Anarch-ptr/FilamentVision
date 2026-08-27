package com.filamentvision.domain.monitoring

import com.filamentvision.domain.alarm.AlarmState
import com.filamentvision.model.CameraStatus
import com.filamentvision.model.ConnectionState
import com.filamentvision.model.MonitoringSession
import com.filamentvision.model.MonitoringState
import com.filamentvision.model.SessionStatistics
import com.filamentvision.model.SimulationScenario
import com.filamentvision.model.VisionMeasurement

data class MonitoringRuntimeState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val monitoringState: MonitoringState = MonitoringState.IDLE,
    val activeSession: MonitoringSession? = null,
    val latestMeasurement: VisionMeasurement? = null,
    val latestMeasurementTimestamp: Long? = null,
    val alarmState: AlarmState = AlarmState(),
    val statistics: SessionStatistics = SessionStatistics(),
    val cameraAStatus: CameraStatus = CameraStatus.OFFLINE,
    val cameraBStatus: CameraStatus = CameraStatus.OFFLINE,
    val selectedScenario: SimulationScenario = SimulationScenario.NORMAL,
    val activeProducerCount: Int = 0,
    val totalProducerStarts: Int = 0,
    val emittedMeasurementCount: Long = 0L,
    val realtimeBufferSize: Int = 0,
)
