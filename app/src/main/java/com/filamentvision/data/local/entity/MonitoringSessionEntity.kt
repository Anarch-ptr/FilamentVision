package com.filamentvision.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "monitoring_sessions",
    indices = [Index("status"), Index("startedAt")],
)
data class MonitoringSessionEntity(
    @PrimaryKey val sessionId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val targetDiameter: Double,
    val warningThresholdPercent: Double,
    val criticalThresholdPercent: Double,
    val status: String,
    val endReason: String?,
    val sampleCount: Long,
    val averageDiameter: Double,
    val minimumDiameter: Double,
    val maximumDiameter: Double,
    val standardDeviation: Double,
    val averageConfidence: Double,
    val abnormalEventCount: Int,
)
