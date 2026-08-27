package com.filamentvision.ui.components.chart

data class TrendChartPoint(
    val timestamp: Long,
    val diameterA: Float?,
    val diameterB: Float?,
    val fusedDiameter: Float,
)
