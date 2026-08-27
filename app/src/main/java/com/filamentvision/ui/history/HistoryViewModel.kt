package com.filamentvision.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filamentvision.FilamentVisionApplication
import com.filamentvision.data.repository.PersistedAlarmEvent
import com.filamentvision.domain.trend.StreamingTrendLoader
import com.filamentvision.model.MonitoringSession
import com.filamentvision.ui.components.chart.TrendChartPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HistoricalSessionUiState(
    val session: MonitoringSession? = null,
    val points: List<TrendChartPoint> = emptyList(),
    val alarms: List<PersistedAlarmEvent> = emptyList(),
    val isLoading: Boolean = false,
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FilamentVisionApplication
    private val loader = StreamingTrendLoader(app.measurementRepository)
    private val mutableSelected = MutableStateFlow(HistoricalSessionUiState())
    val selected: StateFlow<HistoricalSessionUiState> = mutableSelected.asStateFlow()
    val sessions = app.sessionRepository.sessions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        emptyList(),
    )

    fun loadSession(sessionId: String) {
        if (mutableSelected.value.session?.id == sessionId && mutableSelected.value.points.isNotEmpty()) return
        viewModelScope.launch {
            mutableSelected.value = HistoricalSessionUiState(isLoading = true)
            val loaded = withContext(Dispatchers.IO) {
                val session = app.sessionRepository.getById(sessionId)
                if (session == null) return@withContext HistoricalSessionUiState()
                val end = session.endedAt ?: System.currentTimeMillis()
                HistoricalSessionUiState(
                    session = session,
                    points = loader.load(session.id, session.startedAt, end),
                    alarms = app.alarmRepository.getAlarmEvents(session.id, session.startedAt, end),
                )
            }
            mutableSelected.value = loaded
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.sessionRepository.delete(sessionId)
            if (mutableSelected.value.session?.id == sessionId) {
                mutableSelected.value = HistoricalSessionUiState()
            }
        }
    }
}
