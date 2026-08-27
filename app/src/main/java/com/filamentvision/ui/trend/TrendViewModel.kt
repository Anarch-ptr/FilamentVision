package com.filamentvision.ui.trend

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filamentvision.FilamentVisionApplication
import com.filamentvision.domain.trend.TrendDownsampler
import com.filamentvision.domain.trend.toChartPoint
import com.filamentvision.model.MonitoringState
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Observes Runtime/Room; it never starts or owns the measurement producer. */
class TrendViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FilamentVisionApplication
    private val runtime = app.monitoringRuntime
    private val mutableState = MutableStateFlow(TrendUiState())
    private var roomRefreshJob: Job? = null
    private var lastRoomRefreshAt = 0L
    val state: StateFlow<TrendUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            runtime.state.collect { runtimeState ->
                val active = runtimeState.activeSession
                mutableState.update {
                    it.copy(
                        isMonitoring = runtimeState.monitoringState == MonitoringState.MONITORING,
                        targetDiameter = active?.targetDiameter ?: it.targetDiameter,
                    )
                }
                if (mutableState.value.window == TrendWindow.THIRTY_SECONDS) {
                    refreshFromRealtime()
                } else if (System.currentTimeMillis() - lastRoomRefreshAt >= ROOM_REFRESH_INTERVAL) {
                    refreshFromRoom()
                }
            }
        }
    }

    fun selectWindow(window: TrendWindow) {
        mutableState.update { it.copy(window = window, inspectedPoint = null, autoFollow = true) }
        if (window == TrendWindow.THIRTY_SECONDS) refreshFromRealtime() else refreshFromRoom(force = true)
    }

    fun toggleCameraA() = mutableState.update { it.copy(showCameraA = !it.showCameraA) }
    fun toggleCameraB() = mutableState.update { it.copy(showCameraB = !it.showCameraB) }
    fun toggleFused() = mutableState.update { it.copy(showFused = !it.showFused) }
    fun inspect(point: com.filamentvision.ui.components.chart.TrendChartPoint?) =
        mutableState.update { it.copy(inspectedPoint = point, autoFollow = point == null) }
    fun jumpToLive() = mutableState.update { it.copy(inspectedPoint = null, autoFollow = true) }

    private fun refreshFromRealtime() {
        val now = runtime.state.value.latestMeasurementTimestamp ?: System.currentTimeMillis()
        val from = now - TrendWindow.THIRTY_SECONDS.durationMillis
        val points = runtime.realtimeMeasurementsSnapshot()
            .asSequence()
            .filter { it.timestamp >= from }
            .map { it.toChartPoint() }
            .toList()
        mutableState.update { it.copy(points = points) }
    }

    private fun refreshFromRoom(force: Boolean = false) {
        if (!force && roomRefreshJob?.isActive == true) return
        val session = runtime.state.value.activeSession ?: return
        val currentWindow = mutableState.value.window
        roomRefreshJob?.cancel()
        roomRefreshJob = viewModelScope.launch {
            val now = runtime.state.value.latestMeasurementTimestamp ?: System.currentTimeMillis()
            val from = now - currentWindow.durationMillis
            val (points, alarms) = withContext(Dispatchers.IO) {
                val persisted = app.measurementRepository.getMeasurements(session.id, from, now)
                val realtimeTail = runtime.realtimeMeasurementsSnapshot().filter { it.timestamp in from..now }
                val chart = (persisted.map { it.measurement } + realtimeTail)
                    .distinctBy { it.timestamp }
                    .sortedBy { it.timestamp }
                    .map { it.toChartPoint() }
                TrendDownsampler.downsample(chart, MAX_DRAW_POINTS) to
                    app.alarmRepository.getAlarmEvents(session.id, from, now)
            }
            lastRoomRefreshAt = System.currentTimeMillis()
            mutableState.update { it.copy(points = points, alarms = alarms) }
        }
    }

    private companion object {
        const val MAX_DRAW_POINTS = 720
        const val ROOM_REFRESH_INTERVAL = 1_000L
    }
}
