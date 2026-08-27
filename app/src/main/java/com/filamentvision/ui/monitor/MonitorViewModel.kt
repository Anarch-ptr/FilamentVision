package com.filamentvision.ui.monitor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filamentvision.FilamentVisionApplication
import com.filamentvision.model.CalibrationStatus
import com.filamentvision.model.ConnectionType
import com.filamentvision.model.ConnectionConfig
import com.filamentvision.model.MonitoringSession
import com.filamentvision.model.MonitoringState
import com.filamentvision.model.SimulationScenario
import com.filamentvision.model.VisionMeasurement
import com.filamentvision.service.MonitoringServiceController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Maps process-scoped Runtime truth to UI state and sends commands. It never owns a producer. */
class MonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FilamentVisionApplication
    private val runtime = app.monitoringRuntime
    private val mutableMonitorUiState = MutableStateFlow(MonitorUiState())
    private val mutableLiveMeasurementUiState = MutableStateFlow(LiveMeasurementUiState())
    private val mutableSessionClockUiState = MutableStateFlow(SessionClockUiState())
    private val mutableDiagnosticsUiState = MutableStateFlow(DiagnosticsUiState())
    private var connectionSettings = app.connectionSettingsRepository.settings.value
    private var calibrationStatus = CalibrationStatus.CALIBRATED
    private var sessionTimerJob: Job? = null
    private var cachedSessions: List<MonitoringSession> = emptyList()

    val monitorUiState: StateFlow<MonitorUiState> = mutableMonitorUiState.asStateFlow()
    val liveMeasurementUiState: StateFlow<LiveMeasurementUiState> = mutableLiveMeasurementUiState.asStateFlow()
    val sessionClockUiState: StateFlow<SessionClockUiState> = mutableSessionClockUiState.asStateFlow()
    val diagnosticsUiState: StateFlow<DiagnosticsUiState> = mutableDiagnosticsUiState.asStateFlow()
    val historySessions: StateFlow<List<MonitoringSession>> = app.sessionRepository.sessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList(),
    )
    val sessionFinalized = runtime.sessionFinalized

    init {
        observeRuntime()
        observeConnectionSettings()
        observeHistoryCache()
        connect()
    }

    fun connect() = launchCommand { runtime.connect() }
    fun reconnect() = launchCommand { runtime.reconnect() }
    fun disconnect() = launchCommand { runtime.disconnect() }

    fun changeConnection(type: ConnectionType) {
        saveConnectionConfig(
            when (type) {
                ConnectionType.WIFI -> connectionSettings.wifi
                ConnectionType.BLUETOOTH -> connectionSettings.bluetooth
            },
        )
    }

    fun saveConnectionConfig(config: ConnectionConfig) {
        app.connectionSettingsRepository.save(config)
        launchCommand { runtime.reconnect() }
    }

    fun startMonitoring() {
        val state = mutableMonitorUiState.value
        if (!state.canStartMonitoring) return
        MonitoringServiceController.start(getApplication(), state.configuration.targetDiameterMm)
    }

    fun stopMonitoring() {
        if (mutableMonitorUiState.value.monitoringState != MonitoringState.MONITORING) return
        MonitoringServiceController.stop(getApplication())
    }

    fun acknowledgeSessionSummary() = runtime.acknowledgeFinalizedSession()

    fun runFakeCalibration() {
        viewModelScope.launch {
            calibrationStatus = CalibrationStatus.CALIBRATING
            mutableMonitorUiState.update { it.copy(calibrationStatus = calibrationStatus) }
            delay(900L)
            calibrationStatus = CalibrationStatus.CALIBRATED
            mutableMonitorUiState.update { it.copy(calibrationStatus = calibrationStatus) }
        }
    }

    fun selectSimulationScenario(scenario: SimulationScenario) = launchCommand {
        runtime.selectSimulationScenario(scenario)
    }

    fun sessionById(id: String): MonitoringSession? =
        runtime.state.value.activeSession?.takeIf { it.id == id } ?: cachedSessions.firstOrNull { it.id == id }

    fun recentMeasurementsSnapshot(): List<VisionMeasurement> = runtime.realtimeMeasurementsSnapshot()

    private fun observeRuntime() {
        viewModelScope.launch {
            runtime.state.collect { state ->
                mutableMonitorUiState.value = MonitorUiState(
                    connectionState = state.connectionState,
                    monitoringState = state.monitoringState,
                    connectionType = connectionSettings.selectedType,
                    connectionSettings = connectionSettings,
                    cameraAStatus = state.cameraAStatus,
                    cameraBStatus = state.cameraBStatus,
                    calibrationStatus = calibrationStatus,
                    selectedScenario = state.selectedScenario,
                    activeFakeProducerCount = state.activeProducerCount,
                )
                mutableLiveMeasurementUiState.value = LiveMeasurementUiState(
                    latestMeasurement = state.latestMeasurement,
                    bufferedSampleCount = state.realtimeBufferSize,
                )
                mutableDiagnosticsUiState.value = DiagnosticsUiState(
                    activeProducerCount = state.activeProducerCount,
                    totalProducerStarts = state.totalProducerStarts,
                    emittedMeasurementCount = state.emittedMeasurementCount,
                    realtimeBufferSize = state.realtimeBufferSize,
                )
                updateSessionTimer(state.monitoringState, state.activeSession)
            }
        }
    }

    private fun observeHistoryCache() {
        viewModelScope.launch { historySessions.collect { cachedSessions = it } }
    }

    private fun observeConnectionSettings() {
        viewModelScope.launch {
            app.connectionSettingsRepository.settings.collect { settings ->
                connectionSettings = settings
                mutableMonitorUiState.update {
                    it.copy(connectionType = settings.selectedType, connectionSettings = settings)
                }
            }
        }
    }

    private fun updateSessionTimer(state: MonitoringState, session: MonitoringSession?) {
        if (state == MonitoringState.MONITORING && session != null) {
            if (sessionTimerJob?.isActive == true) return
            sessionTimerJob = viewModelScope.launch {
                while (isActive) {
                    mutableSessionClockUiState.value = SessionClockUiState(
                        elapsedMillis = (System.currentTimeMillis() - session.startedAt).coerceAtLeast(0L),
                    )
                    delay(1_000L)
                }
            }
        } else {
            sessionTimerJob?.cancel()
            sessionTimerJob = null
            mutableSessionClockUiState.value = SessionClockUiState(session?.durationMillis ?: 0L)
        }
    }

    private fun launchCommand(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
