package com.filamentvision.domain.session

import com.filamentvision.data.HistoryRepository
import com.filamentvision.model.MeasurementStatus
import com.filamentvision.model.MonitoringEvent
import com.filamentvision.model.MonitoringEventType
import com.filamentvision.model.MonitoringSession
import com.filamentvision.model.SessionStatus
import com.filamentvision.model.VisionMeasurement

/** Owns official-session recording independently from the always-on connected preview. */
class MonitoringSessionController(
    private val historyRepository: HistoryRepository,
    private val nowMillis: () -> Long,
) {
    private var activeSessionId: String? = null
    private var startedAt: Long? = null
    private var targetDiameter = 0.0
    private var statistics = SessionStatisticsAccumulator()
    private val events = ArrayList<MonitoringEvent>()
    private var lastEventType: MonitoringEventType? = null
    private var sessionSequence = 0L

    val isActive: Boolean get() = activeSessionId != null
    val activeStartedAt: Long? get() = startedAt

    fun start(targetDiameter: Double): MonitoringSession {
        check(!isActive) { "A monitoring session is already active" }
        val startTime = nowMillis()
        sessionSequence += 1
        activeSessionId = "session-$startTime-$sessionSequence"
        startedAt = startTime
        this.targetDiameter = targetDiameter
        statistics = SessionStatisticsAccumulator()
        events.clear()
        lastEventType = null
        return buildSession(SessionStatus.ACTIVE, endedAt = null, endReason = null)
    }

    fun record(measurement: VisionMeasurement) {
        if (!isActive) return
        statistics.add(measurement)

        val eventType = measurement.eventType(targetDiameter)
        if (eventType == null) {
            lastEventType = null
            return
        }
        if (eventType == lastEventType || events.size >= MAX_RECORDED_EVENTS) return

        events += MonitoringEvent(
            timestamp = measurement.timestamp,
            type = eventType,
            measurement = measurement,
            description = eventType.description,
        )
        lastEventType = eventType
    }

    fun complete(reason: String = "Stopped by user"): MonitoringSession =
        finalize(SessionStatus.COMPLETED, reason)

    fun interrupt(
        reason: String,
        eventType: MonitoringEventType? = null,
    ): MonitoringSession {
        if (eventType != null && events.size < MAX_RECORDED_EVENTS && eventType != lastEventType) {
            events += MonitoringEvent(
                timestamp = nowMillis(),
                type = eventType,
                description = reason,
            )
        }
        return finalize(SessionStatus.INTERRUPTED, reason)
    }

    private fun finalize(status: SessionStatus, reason: String): MonitoringSession {
        check(isActive) { "There is no active monitoring session" }
        val completed = buildSession(status, endedAt = nowMillis(), endReason = reason)
        historyRepository.save(completed)
        activeSessionId = null
        startedAt = null
        lastEventType = null
        return completed
    }

    private fun buildSession(
        status: SessionStatus,
        endedAt: Long?,
        endReason: String?,
    ): MonitoringSession = MonitoringSession(
        id = checkNotNull(activeSessionId),
        startedAt = checkNotNull(startedAt),
        endedAt = endedAt,
        targetDiameter = targetDiameter,
        status = status,
        statistics = statistics.snapshot(events.size),
        events = events.toList(),
        endReason = endReason,
    )

    private fun VisionMeasurement.eventType(target: Double): MonitoringEventType? = when (status) {
        MeasurementStatus.OUT_OF_TOLERANCE,
        MeasurementStatus.WARNING,
        -> if (fusedDiameter >= target) {
            MonitoringEventType.HIGH_DIAMETER
        } else {
            MonitoringEventType.LOW_DIAMETER
        }
        MeasurementStatus.CAMERA_MISMATCH -> MonitoringEventType.CAMERA_MISMATCH
        MeasurementStatus.LOW_CONFIDENCE -> MonitoringEventType.LOW_CONFIDENCE
        MeasurementStatus.CAMERA_A_FAILURE -> MonitoringEventType.CAMERA_A_FAILURE
        MeasurementStatus.CAMERA_B_FAILURE -> MonitoringEventType.CAMERA_B_FAILURE
        MeasurementStatus.NORMAL,
        MeasurementStatus.DISCONNECTED,
        -> null
    }

    private val MonitoringEventType.description: String
        get() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

    private companion object {
        const val MAX_RECORDED_EVENTS = 300
    }
}
