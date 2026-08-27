package com.filamentvision.domain.session

import com.filamentvision.model.SessionStatistics
import com.filamentvision.model.VisionMeasurement
import kotlin.math.sqrt

/** Incremental Welford statistics: constant memory and no full-session scans. */
class SessionStatisticsAccumulator {
    private var sampleCount = 0L
    private var meanDiameter = 0.0
    private var diameterM2 = 0.0
    private var minimumDiameter = Double.POSITIVE_INFINITY
    private var maximumDiameter = Double.NEGATIVE_INFINITY
    private var confidenceSum = 0.0

    fun add(measurement: VisionMeasurement) {
        sampleCount += 1
        val delta = measurement.fusedDiameter - meanDiameter
        meanDiameter += delta / sampleCount
        val deltaAfterMeanUpdate = measurement.fusedDiameter - meanDiameter
        diameterM2 += delta * deltaAfterMeanUpdate
        minimumDiameter = minOf(minimumDiameter, measurement.fusedDiameter)
        maximumDiameter = maxOf(maximumDiameter, measurement.fusedDiameter)
        confidenceSum += measurement.confidence
    }

    fun snapshot(abnormalEventCount: Int): SessionStatistics {
        if (sampleCount == 0L) return SessionStatistics(abnormalEventCount = abnormalEventCount)

        return SessionStatistics(
            sampleCount = sampleCount,
            averageDiameter = meanDiameter,
            minimumDiameter = minimumDiameter,
            maximumDiameter = maximumDiameter,
            standardDeviation = sqrt(diameterM2 / sampleCount),
            averageConfidence = confidenceSum / sampleCount,
            abnormalEventCount = abnormalEventCount,
        )
    }
}
