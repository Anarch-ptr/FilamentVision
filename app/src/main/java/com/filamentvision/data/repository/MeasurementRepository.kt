package com.filamentvision.data.repository

import com.filamentvision.model.VisionMeasurement

data class PersistedMeasurement(
    val id: Long = 0L,
    val sessionId: String,
    val measurement: VisionMeasurement,
    val sourceType: MeasurementSourceType = MeasurementSourceType.REAL,
)

enum class MeasurementSourceType { REAL }

interface MeasurementRepository {
    suspend fun insertMeasurements(measurements: List<PersistedMeasurement>)
    suspend fun getMeasurements(
        sessionId: String,
        fromTimestamp: Long,
        toTimestamp: Long,
    ): List<PersistedMeasurement>
    suspend fun getMeasurementPage(
        sessionId: String,
        fromTimestamp: Long,
        toTimestamp: Long,
        afterTimestamp: Long,
        afterId: Long,
        limit: Int,
    ): List<PersistedMeasurement>
    suspend fun countMeasurements(sessionId: String): Long
}
