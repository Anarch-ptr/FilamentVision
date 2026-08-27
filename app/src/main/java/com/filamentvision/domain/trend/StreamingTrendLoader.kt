package com.filamentvision.domain.trend

import com.filamentvision.data.repository.MeasurementRepository
import com.filamentvision.ui.components.chart.TrendChartPoint

/** Reads long sessions page-by-page and keeps only spike-preserving chart candidates in memory. */
class StreamingTrendLoader(private val repository: MeasurementRepository) {
    suspend fun load(
        sessionId: String,
        fromTimestamp: Long,
        toTimestamp: Long,
        maxPoints: Int = 720,
    ): List<TrendChartPoint> {
        var afterTimestamp = Long.MIN_VALUE
        var afterId = Long.MIN_VALUE
        var reduced = emptyList<TrendChartPoint>()
        while (true) {
            val page = repository.getMeasurementPage(
                sessionId = sessionId,
                fromTimestamp = fromTimestamp,
                toTimestamp = toTimestamp,
                afterTimestamp = afterTimestamp,
                afterId = afterId,
                limit = PAGE_SIZE,
            )
            if (page.isEmpty()) break
            val chartPage = page.map { row -> row.measurement.toChartPoint() }
            reduced = TrendDownsampler.downsample(reduced + chartPage, maxPoints)
            val last = page.last()
            afterTimestamp = last.measurement.timestamp
            afterId = last.id
            if (page.size < PAGE_SIZE) break
        }
        return reduced
    }

    private companion object { const val PAGE_SIZE = 1_024 }
}

fun com.filamentvision.model.VisionMeasurement.toChartPoint() = TrendChartPoint(
    timestamp = timestamp,
    diameterA = diameterA.toFloat(),
    diameterB = diameterB.toFloat(),
    fusedDiameter = fusedDiameter.toFloat(),
)
