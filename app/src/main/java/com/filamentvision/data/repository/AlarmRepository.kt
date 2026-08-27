package com.filamentvision.data.repository

import com.filamentvision.domain.alarm.AlarmDirection
import com.filamentvision.domain.alarm.AlarmLevel

data class PersistedAlarmEvent(
    val id: Long = 0L,
    val sessionId: String,
    val timestamp: Long,
    val fromLevel: AlarmLevel,
    val toLevel: AlarmLevel,
    val direction: AlarmDirection,
    val fusedDiameter: Double,
    val deviationPercent: Double,
    val description: String,
)

interface AlarmRepository {
    suspend fun insertAlarmEvents(events: List<PersistedAlarmEvent>)
    suspend fun getAlarmEvents(
        sessionId: String,
        fromTimestamp: Long,
        toTimestamp: Long,
    ): List<PersistedAlarmEvent>
    suspend fun countAlarmEvents(sessionId: String): Long
}
