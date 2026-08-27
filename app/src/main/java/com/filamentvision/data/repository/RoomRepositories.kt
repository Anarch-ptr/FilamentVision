package com.filamentvision.data.repository

import androidx.room.withTransaction
import com.filamentvision.data.local.AlarmEventDao
import com.filamentvision.data.local.FilamentVisionDatabase
import com.filamentvision.data.local.MeasurementDao
import com.filamentvision.data.local.SessionDao
import com.filamentvision.data.local.entity.AlarmEventEntity
import com.filamentvision.data.local.entity.MeasurementEntity
import com.filamentvision.data.local.entity.MonitoringSessionEntity
import com.filamentvision.domain.alarm.AlarmDirection
import com.filamentvision.domain.alarm.AlarmLevel
import com.filamentvision.model.MeasurementStatus
import com.filamentvision.model.MonitoringSession
import com.filamentvision.model.SessionStatus
import com.filamentvision.model.VisionMeasurement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSessionRepository(
    private val database: FilamentVisionDatabase,
    private val dao: SessionDao = database.sessionDao(),
) : SessionRepository {
    override val sessions: Flow<List<MonitoringSession>> = dao.observeAll().map { rows ->
        rows.map(MonitoringSessionEntity::toDomain)
    }

    override suspend fun createActive(session: MonitoringSession) {
        dao.insert(session.toEntity())
    }

    override suspend fun finalize(session: MonitoringSession) {
        dao.update(session.toEntity())
    }

    override suspend fun getById(sessionId: String): MonitoringSession? = dao.getById(sessionId)?.toDomain()

    override suspend fun delete(sessionId: String) {
        database.withTransaction { dao.deleteById(sessionId) }
    }

    override suspend fun recoverInterruptedSessions(): Int = dao.recoverActiveSessions()
}

class RoomMeasurementRepository(
    private val dao: MeasurementDao,
) : MeasurementRepository {
    override suspend fun insertMeasurements(measurements: List<PersistedMeasurement>) {
        if (measurements.isNotEmpty()) dao.insertAll(measurements.map(PersistedMeasurement::toEntity))
    }

    override suspend fun getMeasurements(
        sessionId: String,
        fromTimestamp: Long,
        toTimestamp: Long,
    ): List<PersistedMeasurement> = dao.getRange(sessionId, fromTimestamp, toTimestamp).map(MeasurementEntity::toDomain)

    override suspend fun getMeasurementPage(
        sessionId: String,
        fromTimestamp: Long,
        toTimestamp: Long,
        afterTimestamp: Long,
        afterId: Long,
        limit: Int,
    ): List<PersistedMeasurement> = dao.getPage(
        sessionId,
        fromTimestamp,
        toTimestamp,
        afterTimestamp,
        afterId,
        limit,
    ).map(MeasurementEntity::toDomain)

    override suspend fun countMeasurements(sessionId: String): Long = dao.countForSession(sessionId)
}

class RoomAlarmRepository(
    private val dao: AlarmEventDao,
) : AlarmRepository {
    override suspend fun insertAlarmEvents(events: List<PersistedAlarmEvent>) {
        if (events.isNotEmpty()) dao.insertAll(events.map(PersistedAlarmEvent::toEntity))
    }

    override suspend fun getAlarmEvents(
        sessionId: String,
        fromTimestamp: Long,
        toTimestamp: Long,
    ): List<PersistedAlarmEvent> = dao.getRange(sessionId, fromTimestamp, toTimestamp).map(AlarmEventEntity::toDomain)

    override suspend fun countAlarmEvents(sessionId: String): Long = dao.countForSession(sessionId)
}

private fun MonitoringSession.toEntity() = MonitoringSessionEntity(
    sessionId = id,
    startedAt = startedAt,
    endedAt = endedAt,
    targetDiameter = targetDiameter,
    warningThresholdPercent = WARNING_PERCENT,
    criticalThresholdPercent = CRITICAL_PERCENT,
    status = status.name,
    endReason = endReason,
    sampleCount = statistics.sampleCount,
    averageDiameter = statistics.averageDiameter,
    minimumDiameter = statistics.minimumDiameter,
    maximumDiameter = statistics.maximumDiameter,
    standardDeviation = statistics.standardDeviation,
    averageConfidence = statistics.averageConfidence,
    abnormalEventCount = statistics.abnormalEventCount,
)

private fun MonitoringSessionEntity.toDomain() = MonitoringSession(
    id = sessionId,
    startedAt = startedAt,
    endedAt = endedAt,
    targetDiameter = targetDiameter,
    status = SessionStatus.valueOf(status),
    statistics = com.filamentvision.model.SessionStatistics(
        sampleCount = sampleCount,
        averageDiameter = averageDiameter,
        minimumDiameter = minimumDiameter,
        maximumDiameter = maximumDiameter,
        standardDeviation = standardDeviation,
        averageConfidence = averageConfidence,
        abnormalEventCount = abnormalEventCount,
    ),
    events = emptyList(),
    endReason = endReason,
)

private fun PersistedMeasurement.toEntity() = MeasurementEntity(
    id = id,
    sessionId = sessionId,
    timestamp = measurement.timestamp,
    diameterA = measurement.diameterA,
    diameterB = measurement.diameterB,
    fusedDiameter = measurement.fusedDiameter,
    shapeDifference = measurement.shapeDifference,
    cameraAConfidence = measurement.cameraAConfidence,
    cameraBConfidence = measurement.cameraBConfidence,
    confidence = measurement.confidence,
    measurementStatus = measurement.status.name,
)

private fun MeasurementEntity.toDomain() = PersistedMeasurement(
    id = id,
    sessionId = sessionId,
    measurement = VisionMeasurement(
        diameterA = diameterA,
        diameterB = diameterB,
        fusedDiameter = fusedDiameter,
        shapeDifference = shapeDifference,
        cameraAConfidence = cameraAConfidence,
        cameraBConfidence = cameraBConfidence,
        confidence = confidence,
        status = MeasurementStatus.valueOf(measurementStatus),
        timestamp = timestamp,
    ),
)

private fun PersistedAlarmEvent.toEntity() = AlarmEventEntity(
    id = id,
    sessionId = sessionId,
    timestamp = timestamp,
    fromLevel = fromLevel.name,
    toLevel = toLevel.name,
    direction = direction.name,
    fusedDiameter = fusedDiameter,
    deviationPercent = deviationPercent,
    description = description,
)

private fun AlarmEventEntity.toDomain() = PersistedAlarmEvent(
    id = id,
    sessionId = sessionId,
    timestamp = timestamp,
    fromLevel = AlarmLevel.valueOf(fromLevel),
    toLevel = AlarmLevel.valueOf(toLevel),
    direction = AlarmDirection.valueOf(direction),
    fusedDiameter = fusedDiameter,
    deviationPercent = deviationPercent,
    description = description,
)

private const val WARNING_PERCENT = 0.02
private const val CRITICAL_PERCENT = 0.05
