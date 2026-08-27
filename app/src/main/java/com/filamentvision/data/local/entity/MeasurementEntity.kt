package com.filamentvision.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "measurements",
    foreignKeys = [
        ForeignKey(
            entity = MonitoringSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "timestamp"])],
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val timestamp: Long,
    val diameterA: Double,
    val diameterB: Double,
    val fusedDiameter: Double,
    val shapeDifference: Double,
    val cameraAConfidence: Double,
    val cameraBConfidence: Double,
    val confidence: Double,
    val measurementStatus: String,
)
