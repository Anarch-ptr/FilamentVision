package com.filamentvision.ui.monitor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filamentvision.FilamentVisionApplication
import com.filamentvision.model.CalibrationStatus
import com.filamentvision.model.ConnectionProfile
import com.filamentvision.model.MonitoringState
import com.filamentvision.model.MonitoringSession
import com.filamentvision.model.VisionMeasurement
import com.filamentvision.domain.error.ErrorState
import com.filamentvision.domain.error.ErrorCategory
import com.filamentvision.domain.connection.ConnectionProfileValidator
import com.filamentvision.domain.connection.ProfileValidation
import com.filamentvision.service.MonitoringServiceController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private var connectionProfile = app.connectionSettingsRepository.profile.value
    private var calibrationStatus = calibrationStatusFor(connectionProfile)
    private var sessionTimerJob: Job? = null
    private var activeSystemErrorId: Long? = null
    private var activeSystemErrorTitle: String? = null

    val monitorUiState: StateFlow<MonitorUiState> = mutableMonitorUiState.asStateFlow()
    val liveMeasurementUiState: StateFlow<LiveMeasurementUiState> = mutableLiveMeasurementUiState.asStateFlow()
    val sessionClockUiState: StateFlow<SessionClockUiState> = mutableSessionClockUiState.asStateFlow()
    val diagnosticsUiState: StateFlow<DiagnosticsUiState> = mutableDiagnosticsUiState.asStateFlow()
    val sessionFinalized = runtime.sessionFinalized
    val latestCameraFrameCache get() = app.latestCameraFrameCache
    val errorRecorder get() = app.errorRecorder
    val visionPipelineRuntime get() = app.visionPipelineRuntime
    val connectionSettingsRepository get() = app.connectionSettingsRepository
    val monitoringRuntime get() = app.monitoringRuntime

    init {
        observeRuntime()
        observeConnectionSettings()
        observeSystemErrors()
    }

    fun connect() = launchCommand { runtime.connect() }
    fun reconnect() = launchCommand { runtime.reconnect() }
    fun disconnect() = launchCommand { runtime.disconnect() }

    fun saveConnectionProfile(profile: ConnectionProfile) {
        val result = app.connectionSettingsRepository.save(profile)
        if (result is com.filamentvision.domain.connection.ProfileValidation.Valid) {
            launchCommand { runtime.reconnect() }
        }
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

    fun sessionById(id: String) = runtime.state.value.activeSession?.takeIf { it.id == id }

    fun recentMeasurementsSnapshot(): List<VisionMeasurement> = runtime.realtimeMeasurementsSnapshot()

    private fun observeRuntime() {
        viewModelScope.launch {
            runtime.state.collect { state ->
                mutableMonitorUiState.value = MonitorUiState(
                    connectionState = state.connectionState,
                    inputState = state.inputState,
                    monitoringState = state.monitoringState,
                    connectionProfile = connectionProfile,
                    cameraAStatus = state.cameraAStatus,
                    cameraBStatus = state.cameraBStatus,
                    calibrationStatus = calibrationStatus,
                    activeProducerCount = state.activeProducerCount,
                    activeSystemErrorId = activeSystemErrorId,
                    activeSystemErrorTitle = activeSystemErrorTitle,
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

    private fun observeSystemErrors() {
        viewModelScope.launch {
            app.errorRepository.errors.collect { errors ->
                val currentSessionId = runtime.state.value.activeSession?.id
                val active = errors.firstOrNull {
                    it.state == ErrorState.ACTIVE &&
                        it.category in OPERATIONAL_ERROR_CATEGORIES &&
                        (it.sessionId == null || it.sessionId == currentSessionId)
                }
                activeSystemErrorId = active?.id
                activeSystemErrorTitle = active?.title
                mutableMonitorUiState.update { it.copy(activeSystemErrorId = active?.id, activeSystemErrorTitle = active?.title) }
            }
        }
    }

    private companion object {
        val OPERATIONAL_ERROR_CATEGORIES = setOf(
            ErrorCategory.CONNECTION, ErrorCategory.CAMERA, ErrorCategory.MONITORING,
            ErrorCategory.SERVICE, ErrorCategory.VISUAL_PROCESSING,
        )
    }

    private fun observeConnectionSettings() {
        viewModelScope.launch {
            app.connectionSettingsRepository.profile.collect { profile ->
                connectionProfile = profile
                calibrationStatus = calibrationStatusFor(profile)
                mutableMonitorUiState.update {
                    it.copy(connectionProfile = profile, calibrationStatus = calibrationStatus)
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

    private fun calibrationStatusFor(profile: ConnectionProfile): CalibrationStatus =
        if (ConnectionProfileValidator.validate(profile) == ProfileValidation.Valid &&
            profile.cameras.all { it.calibration.mmPerPixel > 0.0 }
        ) CalibrationStatus.CALIBRATED else CalibrationStatus.NOT_CALIBRATED
}
