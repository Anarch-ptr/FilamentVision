package com.filamentvision.ui.trend

import com.filamentvision.data.repository.PersistedAlarmEvent
import com.filamentvision.ui.components.chart.TrendChartPoint

enum class TrendWindow(val durationMillis: Long, val label: String) {
    THIRTY_SECONDS(30_000L, "30 s"),
    ONE_MINUTE(60_000L, "1 min"),
    FIVE_MINUTES(300_000L, "5 min"),
}

data class TrendUiState(
    val points: List<TrendChartPoint> = emptyList(),
    val alarms: List<PersistedAlarmEvent> = emptyList(),
    val window: TrendWindow = TrendWindow.THIRTY_SECONDS,
    val targetDiameter: Double = 1.75,
    val isMonitoring: Boolean = false,
    val showCameraA: Boolean = true,
    val showCameraB: Boolean = true,
    val showFused: Boolean = true,
    val autoFollow: Boolean = true,
    val inspectedPoint: TrendChartPoint? = null,
)
