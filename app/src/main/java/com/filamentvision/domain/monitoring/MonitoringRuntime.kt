package com.filamentvision.domain.monitoring

import com.filamentvision.data.settings.SavedConnectionProfile
import com.filamentvision.data.repository.AlarmRepository
import com.filamentvision.data.repository.MeasurementRepository
import com.filamentvision.data.repository.PersistedAlarmEvent
import com.filamentvision.data.repository.PersistedMeasurement
import com.filamentvision.data.repository.SessionRepository
import com.filamentvision.domain.alarm.AlarmEvaluator
import com.filamentvision.domain.alarm.AlarmLevel
import com.filamentvision.domain.error.ErrorCategory
import com.filamentvision.domain.error.ErrorEpisodeIdentity
import com.filamentvision.domain.error.ErrorRecorder
import com.filamentvision.domain.error.ErrorSeverity
import com.filamentvision.domain.error.NewErrorRecord
import com.filamentvision.domain.error.NoOpErrorRecorder
import com.filamentvision.domain.monitor.RealtimeMeasurementBuffer
import com.filamentvision.domain.session.SessionStatisticsAccumulator
import com.filamentvision.hardware.DeviceConnection
import com.filamentvision.hardware.VisionSource
import com.filamentvision.input.InputState
import com.filamentvision.model.CameraStatus
import com.filamentvision.model.ConnectionState
import com.filamentvision.model.MonitoringSession
import com.filamentvision.model.MonitoringState
import com.filamentvision.model.SessionStatus
import com.filamentvision.model.VisionMeasurement
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
    private val deviceConnection: DeviceConnection,
    private val visionSource: VisionSource,
    private val sessionRepositoryProvider: () -> SessionRepository,
    private val measurementRepositoryProvider: () -> MeasurementRepository,
    private val alarmRepositoryProvider: () -> AlarmRepository,
    private val errorRecorder: ErrorRecorder = NoOpErrorRecorder,
    private val dispatcher: CoroutineDispatcher,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val initialAppliedProfileRevision: Long = 0L,
    private val configurationApplier: suspend (SavedConnectionProfile) -> Unit = {},
) {
    constructor(
        deviceConnection: DeviceConnection,
        visionSource: VisionSource,
        sessionRepository: SessionRepository,
        measurementRepository: MeasurementRepository,
        alarmRepository: AlarmRepository,
        errorRecorder: ErrorRecorder = NoOpErrorRecorder,
        dispatcher: CoroutineDispatcher,
        nowMillis: () -> Long = System::currentTimeMillis,
        initialAppliedProfileRevision: Long = 0L,
        configurationApplier: suspend (SavedConnectionProfile) -> Unit = {},
    ) : this(
        deviceConnection = deviceConnection,
        visionSource = visionSource,
        sessionRepositoryProvider = { sessionRepository },
        measurementRepositoryProvider = { measurementRepository },
        alarmRepositoryProvider = { alarmRepository },
        errorRecorder = errorRecorder,
        dispatcher = dispatcher,
        nowMillis = nowMillis,
        initialAppliedProfileRevision = initialAppliedProfileRevision,
        configurationApplier = configurationApplier,
    )

    private val sessionRepository: SessionRepository by lazy(sessionRepositoryProvider)
    private val measurementRepository: MeasurementRepository by lazy(measurementRepositoryProvider)
    private val alarmRepository: AlarmRepository by lazy(alarmRepositoryProvider)
    private val runtimeScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lifecycleMutex = Mutex()
    private val realtimeBuffer = RealtimeMeasurementBuffer<VisionMeasurement>(REALTIME_CAPACITY)
    private val mutableState = MutableStateFlow(
        MonitoringRuntimeState(
            inputState = visionSource.inputState.value,
            appliedProfileRevision = initialAppliedProfileRevision,
        ),
    )
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
    private var connectionErrorIdentity: ErrorEpisodeIdentity? = null
    private var cameraAErrorIdentity: ErrorEpisodeIdentity? = null
    private var cameraBErrorIdentity: ErrorEpisodeIdentity? = null
    private val interruptedSessionRecovery by lazy {
        runtimeScope.async {
            val recoveredIds = sessionRepository.recoverInterruptedSessionIds()
            if (recoveredIds == null) {
                sessionRepository.recoverInterruptedSessions()
            } else {
                recoveredIds.forEach { sessionId ->
                    errorRecorder.record(
                        NewErrorRecord(
                            ErrorSeverity.ERROR, ErrorCategory.RECOVERY, "PROCESS_TERMINATED",
                            "Monitoring process terminated", "An active monitoring session was interrupted by process termination.",
                            "MonitoringRuntime", sessionId,
                        ),
                    )
                }
                recoveredIds.size
            }
        }
    }

    val state: StateFlow<MonitoringRuntimeState> = mutableState.asStateFlow()
    val sessionFinalized: SharedFlow<String> = mutableSessionFinalized.asSharedFlow()

    init {
        observeConnectionState()
        observeInputState()
        measurementCollectorJob = collectMeasurements()
    }

    // -------------------------------------------------------------------------
    // CONNECTION LIFECYCLE
    // -------------------------------------------------------------------------

    suspend fun connect() {
        recoverInterruptedSessions()
        lifecycleMutex.withLock {
            if (mutableState.value.connectionState == ConnectionState.CONNECTED) return
            mutableState.update {
                it.copy(cameraAStatus = CameraStatus.STARTING, cameraBStatus = CameraStatus.STARTING)
            }
            try {
                deviceConnection.connect()
            } catch (failure: Throwable) {
                recordConnectionFailure("Connection failed", failure)
                throw failure
            }
            val inputState = visionSource.inputState.value
            if (deviceConnection.state.value != ConnectionState.CONNECTED || !inputState.canConnectRuntime()) {
                mutableState.update {
                    it.copy(
                        connectionState = deviceConnection.state.value,
                        inputState = inputState,
                        monitoringState = MonitoringState.IDLE,
                        cameraAStatus = CameraStatus.OFFLINE,
                        cameraBStatus = CameraStatus.OFFLINE,
                        activeProducerCount = 0,
                    )
                }
                return@withLock
            }
            resolveConnectionError()
            resolveCameraError("A", cameraAErrorIdentity) { cameraAErrorIdentity = null }
            resolveCameraError("B", cameraBErrorIdentity) { cameraBErrorIdentity = null }
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
        recoverInterruptedSessions()
        if (isSessionActive()) interruptMonitoring("Connection restarted")
        lifecycleMutex.withLock {
            visionSource.stop()
            try {
                deviceConnection.reconnect()
            } catch (failure: Throwable) {
                recordConnectionFailure("Reconnection failed", failure)
                throw failure
            }
            val inputState = visionSource.inputState.value
            if (deviceConnection.state.value != ConnectionState.CONNECTED || !inputState.canConnectRuntime()) {
                mutableState.update {
                    it.copy(
                        connectionState = deviceConnection.state.value,
                        inputState = inputState,
                        monitoringState = MonitoringState.IDLE,
                        cameraAStatus = CameraStatus.OFFLINE,
                        cameraBStatus = CameraStatus.OFFLINE,
                        activeProducerCount = 0,
                    )
                }
                return@withLock
            }
            resolveConnectionError()
            resolveCameraError("A", cameraAErrorIdentity) { cameraAErrorIdentity = null }
            resolveCameraError("B", cameraBErrorIdentity) { cameraBErrorIdentity = null }
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
        recoverInterruptedSessions()
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
        recoverInterruptedSessions()
        lifecycleMutex.withLock {
            val current = mutableState.value
            if (
                current.connectionState != ConnectionState.CONNECTED ||
                !current.inputState.canStartMonitoring() ||
                current.monitoringState == MonitoringState.STARTING ||
                current.monitoringState == MonitoringState.MONITORING
            ) return

            mutableState.update { it.copy(monitoringState = MonitoringState.STARTING) }
            val session = createActiveSession(targetDiameter)
            try {
                sessionRepository.createActive(session)
                prepareSessionRecording(session)
                visionSource.start()
            } catch (failure: Throwable) {
                acceptingMeasurements = false
                errorRecorder.record(
                    NewErrorRecord(
                        ErrorSeverity.ERROR, ErrorCategory.MONITORING, "MONITORING_RUNTIME_ERROR",
                        "Monitoring failed to start", failure.message ?: "The monitoring producer could not start.",
                        "MonitoringRuntime", session.id,
                    ),
                )
                activeSession = null
                mutableState.update {
                    it.copy(
                        monitoringState = MonitoringState.READY,
                        activeSession = null,
                        activeProducerCount = 0,
                    )
                }
                return@withLock
            }
            mutableState.update {
                it.copy(
                    monitoringState = MonitoringState.MONITORING,
                    activeSession = session,
                    cameraAStatus = CameraStatus.LIVE,
                    cameraBStatus = CameraStatus.LIVE,
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

    suspend fun applyConnectionProfile(saved: SavedConnectionProfile): ApplyConfigurationResult {
        if (saved.revision == mutableState.value.appliedProfileRevision) return ApplyConfigurationResult.ALREADY_APPLIED
        val targetDiameter = activeSession?.targetDiameter
        if (isSessionActive()) finalizeSession(SessionStatus.INTERRUPTED, "CONFIGURATION_CHANGED")
        configurationApplier(saved)
        mutableState.update { it.copy(appliedProfileRevision = saved.revision) }
        if (targetDiameter != null &&
            mutableState.value.connectionState == ConnectionState.CONNECTED &&
            visionSource.inputState.value.canStartMonitoring()
        ) {
            acknowledgeFinalizedSession()
            startMonitoring(targetDiameter)
            return ApplyConfigurationResult.APPLIED_NEW_SESSION
        }
        return ApplyConfigurationResult.APPLIED
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
            try {
                measurementWriteBuffer?.close()
                alarmWriteBuffer?.close()
            } catch (failure: Throwable) {
                errorRecorder.record(
                    NewErrorRecord(
                        ErrorSeverity.ERROR, ErrorCategory.DATABASE, "DATABASE_WRITE_FAILED",
                        "Final buffered data could not be written", failure.message ?: "Final Room flush failed.",
                        "MeasurementWriteBuffer", activeSession?.id,
                    ),
                )
            }
            measurementWriteBuffer = null
            alarmWriteBuffer = null
            val base = checkNotNull(activeSession)
            val finalized = base.copy(
                endedAt = nowMillis(),
                status = status,
                statistics = statistics.snapshot(abnormalEventCount),
                endReason = reason,
            )
            try {
                sessionRepository.finalize(finalized)
            } catch (failure: Throwable) {
                errorRecorder.record(
                    NewErrorRecord(
                        ErrorSeverity.ERROR, ErrorCategory.SESSION, "SESSION_FINALIZE_FAILED",
                        "Session finalisation failed", failure.message ?: "The session summary could not be persisted.",
                        "SessionRepository", finalized.id,
                    ),
                )
            }
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
        val measurementSink = measurementRepository
        val alarmSink = alarmRepository
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
            writeBatch = { batch ->
                try {
                    measurementSink.insertMeasurements(batch)
                } catch (failure: Throwable) {
                    errorRecorder.record(
                        NewErrorRecord(
                            ErrorSeverity.ERROR, ErrorCategory.DATABASE, "DATABASE_WRITE_FAILED",
                            "Measurement persistence failed", failure.message ?: "Room failed to write measurements.",
                            "MeasurementRepository", session.id,
                        ),
                    )
                    throw failure
                }
            },
        )
        alarmWriteBuffer = MeasurementWriteBuffer(
            scope = runtimeScope,
            batchSize = WRITE_BATCH_SIZE,
            flushIntervalMillis = WRITE_INTERVAL_MILLIS,
            dispatcher = dispatcher,
            writeBatch = { batch ->
                try {
                    alarmSink.insertAlarmEvents(batch)
                } catch (failure: Throwable) {
                    errorRecorder.record(
                        NewErrorRecord(
                            ErrorSeverity.ERROR, ErrorCategory.DATABASE, "DATABASE_WRITE_FAILED",
                            "Alarm persistence failed", failure.message ?: "Room failed to write alarm events.",
                            "AlarmRepository", session.id,
                        ),
                    )
                    throw failure
                }
            },
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

    private suspend fun recordRootError(record: NewErrorRecord): ErrorEpisodeIdentity {
        errorRecorder.record(record)
        return record.identity
    }

    private suspend fun resolveConnectionError() {
        connectionErrorIdentity?.let { errorRecorder.resolve(it) }
        errorRecorder.resolveMatching("CONNECTION_LOST", "DeviceConnection", null)
        connectionErrorIdentity = null
    }

    private suspend fun resolveCameraError(cameraId: String, identity: ErrorEpisodeIdentity?, clear: () -> Unit) {
        identity?.let { errorRecorder.resolve(it) }
        errorRecorder.resolveMatching("CAMERA_FAILURE", "Camera", cameraId)
        clear()
    }

    private suspend fun recordConnectionFailure(title: String, failure: Throwable) {
        connectionErrorIdentity = recordRootError(NewErrorRecord(
            ErrorSeverity.ERROR, ErrorCategory.CONNECTION, "CONNECTION_LOST", title,
            failure.message ?: "The monitoring device could not be reached.", "DeviceConnection", activeSession?.id,
        ))
        mutableState.update { it.copy(connectionState = ConnectionState.ERROR, cameraAStatus = CameraStatus.OFFLINE, cameraBStatus = CameraStatus.OFFLINE) }
    }

    suspend fun recoverInterruptedSessions(): Int = interruptedSessionRecovery.await()

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

    private fun observeInputState() {
        runtimeScope.launch {
            visionSource.inputState.collect { inputState ->
                mutableState.update { it.copy(inputState = inputState) }
            }
        }
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

enum class ApplyConfigurationResult { ALREADY_APPLIED, APPLIED, APPLIED_NEW_SESSION }

private fun InputState.canConnectRuntime(): Boolean =
    this is InputState.Connected || this is InputState.WaitingForFrame || this is InputState.Streaming

private fun InputState.canStartMonitoring(): Boolean =
    this is InputState.WaitingForFrame || this is InputState.Streaming
