package com.filamentvision.domain.monitoring

import com.filamentvision.data.repository.AlarmRepository
import com.filamentvision.data.repository.MeasurementRepository
import com.filamentvision.data.repository.PersistedAlarmEvent
import com.filamentvision.data.repository.PersistedMeasurement
import com.filamentvision.data.repository.SessionRepository
import com.filamentvision.domain.alarm.AlarmEvaluator
import com.filamentvision.domain.alarm.AlarmLevel
import com.filamentvision.domain.monitor.RealtimeMeasurementBuffer
import com.filamentvision.domain.session.SessionStatisticsAccumulator
import com.filamentvision.fake.FakeDeviceConnection
import com.filamentvision.fake.FakeVisionSource
import com.filamentvision.model.CameraStatus
import com.filamentvision.model.ConnectionState
import com.filamentvision.model.MonitoringSession
import com.filamentvision.model.MonitoringState
import com.filamentvision.model.SessionStatus
import com.filamentvision.model.SimulationScenario
import com.filamentvision.model.VisionMeasurement
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Per-process monitoring engine. UI and Service observe it; neither owns a producer. */
class MonitoringRuntime(
    private val deviceConnection: FakeDeviceConnection,
    private val visionSource: FakeVisionSource,
    private val sessionRepository: SessionRepository,
    private val measurementRepository: MeasurementRepository,
    private val alarmRepository: AlarmRepository,
    private val dispatcher: CoroutineDispatcher,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val runtimeScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lifecycleMutex = Mutex()
    private val realtimeBuffer = RealtimeMeasurementBuffer<VisionMeasurement>(REALTIME_CAPACITY)
    private val mutableState = MutableStateFlow(MonitoringRuntimeState())
    private val mutableSessionFinalized = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private var measurementCollectorJob: Job
    private var acceptingMeasurements = false
    private var statistics = SessionStatisticsAccumulator()
    private var alarmEvaluator: AlarmEvaluator? = null
    private var measurementWriteBuffer: MeasurementWriteBuffer<PersistedMeasurement>? = null
    private var alarmWriteBuffer: MeasurementWriteBuffer<PersistedAlarmEvent>? = null
    private var activeSession: MonitoringSession? = null
    private var abnormalEventCount = 0
    private var sessionSequence = 0L

    val state: StateFlow<MonitoringRuntimeState> = mutableState.asStateFlow()
    val sessionFinalized: SharedFlow<String> = mutableSessionFinalized.asSharedFlow()

    init {
        observeConnectionState()
        measurementCollectorJob = collectMeasurements()
    }

    // -------------------------------------------------------------------------
    // CONNECTION LIFECYCLE
    // -------------------------------------------------------------------------

    suspend fun connect() {
        lifecycleMutex.withLock {
            if (mutableState.value.connectionState == ConnectionState.CONNECTED) return
            mutableState.update {
                it.copy(cameraAStatus = CameraStatus.STARTING, cameraBStatus = CameraStatus.STARTING)
            }
            deviceConnection.connect()
            mutableState.update {
                it.copy(
                    connectionState = ConnectionState.CONNECTED,
                    monitoringState = MonitoringState.READY,
                    cameraAStatus = CameraStatus.READY,
                    cameraBStatus = CameraStatus.READY,
                    activeProducerCount = 0,
                )
            }
        }
    }

    suspend fun reconnect() {
        if (isSessionActive()) interruptMonitoring("Connection restarted")
        lifecycleMutex.withLock {
            visionSource.stop()
            resetConnectionLostScenario()
            deviceConnection.reconnect()
            mutableState.update {
                it.copy(
                    connectionState = ConnectionState.CONNECTED,
                    monitoringState = MonitoringState.READY,
                    cameraAStatus = CameraStatus.READY,
                    cameraBStatus = CameraStatus.READY,
                    activeProducerCount = 0,
                )
            }
        }
    }

    suspend fun disconnect() {
        if (isSessionActive()) interruptMonitoring("Device disconnected by user")
        lifecycleMutex.withLock {
            visionSource.stop()
            deviceConnection.disconnect()
            realtimeBuffer.clear()
            mutableState.update {
                it.copy(
                    connectionState = ConnectionState.DISCONNECTED,
                    monitoringState = MonitoringState.IDLE,
                    cameraAStatus = CameraStatus.OFFLINE,
                    cameraBStatus = CameraStatus.OFFLINE,
                    activeProducerCount = 0,
                    realtimeBufferSize = 0,
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // SESSION LIFECYCLE
    // -------------------------------------------------------------------------

    suspend fun startMonitoring(targetDiameter: Double) {
        lifecycleMutex.withLock {
            val current = mutableState.value
            if (
                current.connectionState != ConnectionState.CONNECTED ||
                current.monitoringState == MonitoringState.STARTING ||
                current.monitoringState == MonitoringState.MONITORING
            ) return

            mutableState.update { it.copy(monitoringState = MonitoringState.STARTING) }
            val session = createActiveSession(targetDiameter)
            sessionRepository.createActive(session)
            prepareSessionRecording(session)
            visionSource.start()
            mutableState.update {
                it.copy(
                    monitoringState = MonitoringState.MONITORING,
                    activeSession = session,
                    cameraAStatus = cameraStatusForA(it.selectedScenario),
                    cameraBStatus = cameraStatusForB(it.selectedScenario),
                    activeProducerCount = visionSource.activeProducerCount,
                    totalProducerStarts = visionSource.totalProducerStarts,
                )
            }
        }
    }

    suspend fun stopMonitoring() {
        finalizeSession(SessionStatus.COMPLETED, "Stopped by user")
    }

    suspend fun interruptMonitoring(reason: String) {
        finalizeSession(SessionStatus.INTERRUPTED, reason)
    }

    fun acknowledgeFinalizedSession() {
        mutableState.update { current ->
            if (
                current.monitoringState != MonitoringState.COMPLETED &&
                current.monitoringState != MonitoringState.INTERRUPTED
            ) {
                current
            } else {
                current.copy(
                    monitoringState = if (current.connectionState == ConnectionState.CONNECTED) {
                        MonitoringState.READY
                    } else {
                        MonitoringState.IDLE
                    },
                    activeSession = null,
                    alarmState = com.filamentvision.domain.alarm.AlarmState(),
                )
            }
        }
    }

    private suspend fun finalizeSession(status: SessionStatus, reason: String) {
        lifecycleMutex.withLock {
            if (mutableState.value.monitoringState != MonitoringState.MONITORING || activeSession == null) return

            mutableState.update { it.copy(monitoringState = MonitoringState.STOPPING) }
            acceptingMeasurements = false
            visionSource.stop()
            measurementWriteBuffer?.close()
            alarmWriteBuffer?.close()
            measurementWriteBuffer = null
            alarmWriteBuffer = null
            val base = checkNotNull(activeSession)
            val finalized = base.copy(
                endedAt = nowMillis(),
                status = status,
                statistics = statistics.snapshot(abnormalEventCount),
                endReason = reason,
            )
            sessionRepository.finalize(finalized)
            activeSession = null
            alarmEvaluator = null
            mutableState.update {
                it.copy(
                    monitoringState = if (status == SessionStatus.COMPLETED) {
                        MonitoringState.COMPLETED
                    } else {
                        MonitoringState.INTERRUPTED
                    },
                    activeSession = finalized,
                    statistics = finalized.statistics,
                    activeProducerCount = 0,
                    cameraAStatus = if (it.connectionState == ConnectionState.CONNECTED) CameraStatus.READY else CameraStatus.OFFLINE,
                    cameraBStatus = if (it.connectionState == ConnectionState.CONNECTED) CameraStatus.READY else CameraStatus.OFFLINE,
                )
            }
            mutableSessionFinalized.tryEmit(finalized.id)
        }
    }

    private fun createActiveSession(targetDiameter: Double): MonitoringSession {
        sessionSequence += 1
        val startedAt = nowMillis()
        return MonitoringSession(
            id = "session-$startedAt-$sessionSequence",
            startedAt = startedAt,
            targetDiameter = targetDiameter,
            status = SessionStatus.ACTIVE,
            statistics = com.filamentvision.model.SessionStatistics(),
            events = emptyList(),
        )
    }

    private fun prepareSessionRecording(session: MonitoringSession) {
        realtimeBuffer.clear()
        statistics = SessionStatisticsAccumulator()
        abnormalEventCount = 0
        alarmEvaluator = AlarmEvaluator(session.targetDiameter)
        activeSession = session
        measurementWriteBuffer = MeasurementWriteBuffer(
            scope = runtimeScope,
            batchSize = WRITE_BATCH_SIZE,
            flushIntervalMillis = WRITE_INTERVAL_MILLIS,
            dispatcher = dispatcher,
            writeBatch = measurementRepository::insertMeasurements,
        )
        alarmWriteBuffer = MeasurementWriteBuffer(
            scope = runtimeScope,
            batchSize = WRITE_BATCH_SIZE,
            flushIntervalMillis = WRITE_INTERVAL_MILLIS,
            dispatcher = dispatcher,
            writeBatch = alarmRepository::insertAlarmEvents,
        )
        acceptingMeasurements = true
        mutableState.update {
            it.copy(
                activeSession = session,
                statistics = com.filamentvision.model.SessionStatistics(),
                alarmState = com.filamentvision.domain.alarm.AlarmState(),
                realtimeBufferSize = 0,
            )
        }
    }

    // -------------------------------------------------------------------------
    // MEASUREMENT PIPELINE
    // -------------------------------------------------------------------------

    private fun collectMeasurements(): Job = runtimeScope.launch {
        visionSource.measurements.collect { measurement -> recordMeasurement(measurement) }
    }

    private suspend fun recordMeasurement(measurement: VisionMeasurement) {
        if (!acceptingMeasurements) return
        val session = activeSession ?: return
        realtimeBuffer.add(measurement)
        statistics.add(measurement)
        measurementWriteBuffer?.add(PersistedMeasurement(sessionId = session.id, measurement = measurement))

        val evaluation = checkNotNull(alarmEvaluator).evaluate(measurement)
        evaluation.transition?.let { transition ->
            abnormalEventCount += 1
            alarmWriteBuffer?.add(
                PersistedAlarmEvent(
                    sessionId = session.id,
                    timestamp = transition.timestamp,
                    fromLevel = transition.from.level,
                    toLevel = transition.to.level,
                    direction = transition.to.direction,
                    fusedDiameter = transition.fusedDiameter,
                    deviationPercent = transition.to.deviationPercent,
                    description = alarmDescription(transition.to.level),
                ),
            )
        }
        mutableState.update {
            it.copy(
                latestMeasurement = measurement,
                latestMeasurementTimestamp = measurement.timestamp,
                alarmState = evaluation.state,
                statistics = statistics.snapshot(abnormalEventCount),
                activeProducerCount = visionSource.activeProducerCount,
                emittedMeasurementCount = visionSource.emittedSampleCount,
                realtimeBufferSize = realtimeBuffer.size,
            )
        }
    }

    fun realtimeMeasurementsSnapshot(): List<VisionMeasurement> = realtimeBuffer.snapshot()

    // -------------------------------------------------------------------------
    // DIAGNOSTICS / RECOVERY
    // -------------------------------------------------------------------------

    suspend fun selectSimulationScenario(scenario: SimulationScenario) {
        visionSource.selectScenario(scenario)
        mutableState.update {
            it.copy(
                selectedScenario = scenario,
                cameraAStatus = if (it.monitoringState == MonitoringState.MONITORING) cameraStatusForA(scenario) else it.cameraAStatus,
                cameraBStatus = if (it.monitoringState == MonitoringState.MONITORING) cameraStatusForB(scenario) else it.cameraBStatus,
            )
        }
        when (scenario) {
            SimulationScenario.CONNECTION_LOST -> {
                interruptMonitoring("Connection lost")
                deviceConnection.simulateConnectionError()
                mutableState.update {
                    it.copy(
                        connectionState = ConnectionState.ERROR,
                        cameraAStatus = CameraStatus.OFFLINE,
                        cameraBStatus = CameraStatus.OFFLINE,
                    )
                }
            }
            SimulationScenario.CAMERA_A_FAILURE -> {
                interruptMonitoring("Camera A failed")
                mutableState.update { it.copy(cameraAStatus = CameraStatus.ERROR) }
            }
            SimulationScenario.CAMERA_B_FAILURE -> {
                interruptMonitoring("Camera B failed")
                mutableState.update { it.copy(cameraBStatus = CameraStatus.ERROR) }
            }
            else -> Unit
        }
    }

    suspend fun recoverInterruptedSessions(): Int = sessionRepository.recoverInterruptedSessions()

    suspend fun close() {
        if (isSessionActive()) interruptMonitoring("Runtime shutdown")
        acceptingMeasurements = false
        measurementCollectorJob.cancel()
        visionSource.close()
        deviceConnection.close()
        runtimeScope.cancel()
    }

    private fun isSessionActive(): Boolean = mutableState.value.monitoringState == MonitoringState.MONITORING

    private fun observeConnectionState() {
        runtimeScope.launch {
            deviceConnection.state.collect { connectionState ->
                mutableState.update { it.copy(connectionState = connectionState) }
            }
        }
    }

    private fun resetConnectionLostScenario() {
        if (mutableState.value.selectedScenario != SimulationScenario.CONNECTION_LOST) return
        visionSource.selectScenario(SimulationScenario.NORMAL)
        mutableState.update { it.copy(selectedScenario = SimulationScenario.NORMAL) }
    }

    private fun cameraStatusForA(scenario: SimulationScenario): CameraStatus = when (scenario) {
        SimulationScenario.CAMERA_A_FAILURE -> CameraStatus.ERROR
        SimulationScenario.LOW_CONFIDENCE -> CameraStatus.LOW_CONFIDENCE
        else -> CameraStatus.LIVE
    }

    private fun cameraStatusForB(scenario: SimulationScenario): CameraStatus = when (scenario) {
        SimulationScenario.CAMERA_B_FAILURE -> CameraStatus.ERROR
        SimulationScenario.LOW_CONFIDENCE -> CameraStatus.LOW_CONFIDENCE
        else -> CameraStatus.LIVE
    }

    private fun alarmDescription(level: AlarmLevel): String = when (level) {
        AlarmLevel.NORMAL -> "Returned to normal"
        AlarmLevel.WARNING -> "Warning started"
        AlarmLevel.CRITICAL -> "Critical started"
    }

    private companion object {
        const val REALTIME_CAPACITY = 300
        const val WRITE_BATCH_SIZE = 8
        const val WRITE_INTERVAL_MILLIS = 1_000L
    }
}
