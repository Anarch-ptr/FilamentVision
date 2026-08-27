package com.filamentvision.domain.session

import com.filamentvision.fake.InMemoryHistoryRepository
import com.filamentvision.model.MeasurementStatus
import com.filamentvision.model.MonitoringEventType
import com.filamentvision.model.SessionStatus
import com.filamentvision.model.VisionMeasurement
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStatisticsAccumulatorTest {
    @Test
    fun calculatesMinMaxAveragePopulationDeviationConfidenceAndEventCount() {
        val accumulator = SessionStatisticsAccumulator()
        accumulator.add(measurement(diameter = 1.0, confidence = 0.7))
        accumulator.add(measurement(diameter = 2.0, confidence = 0.8))
        accumulator.add(measurement(diameter = 3.0, confidence = 0.9))

        val statistics = accumulator.snapshot(abnormalEventCount = 2)
        assertEquals(3L, statistics.sampleCount)
        assertEquals(2.0, statistics.averageDiameter, 0.000_001)
        assertEquals(1.0, statistics.minimumDiameter, 0.000_001)
        assertEquals(3.0, statistics.maximumDiameter, 0.000_001)
        assertEquals(sqrt(2.0 / 3.0), statistics.standardDeviation, 0.000_001)
        assertEquals(0.8, statistics.averageConfidence, 0.000_001)
        assertEquals(2, statistics.abnormalEventCount)
    }

    @Test
    fun controllerSavesCompletedAndInterruptedSessionsAndDeduplicatesPersistentEvent() {
        var now = 1_000L
        val repository = InMemoryHistoryRepository()
        val controller = MonitoringSessionController(repository) { now }

        controller.start(targetDiameter = 1.75)
        controller.record(measurement(1.79, 0.9, MeasurementStatus.OUT_OF_TOLERANCE, now))
        now += 125L
        controller.record(measurement(1.79, 0.9, MeasurementStatus.OUT_OF_TOLERANCE, now))
        now += 875L
        val completed = controller.complete()

        assertEquals(SessionStatus.COMPLETED, completed.status)
        assertEquals(2L, completed.statistics.sampleCount)
        assertEquals(1, completed.statistics.abnormalEventCount)
        assertEquals(MonitoringEventType.HIGH_DIAMETER, completed.events.single().type)

        now += 1_000L
        controller.start(targetDiameter = 1.75)
        controller.record(measurement(1.75, 0.95, timestamp = now))
        now += 500L
        val interrupted = controller.interrupt("Connection lost", MonitoringEventType.CONNECTION_LOST)

        assertEquals(SessionStatus.INTERRUPTED, interrupted.status)
        assertEquals("Connection lost", interrupted.endReason)
        assertEquals(2, repository.sessions.value.size)
        assertEquals(MonitoringEventType.CONNECTION_LOST, interrupted.events.single().type)
    }

    private fun measurement(
        diameter: Double,
        confidence: Double,
        status: MeasurementStatus = MeasurementStatus.NORMAL,
        timestamp: Long = 0L,
    ) = VisionMeasurement(
        diameterA = diameter,
        diameterB = diameter,
        fusedDiameter = diameter,
        shapeDifference = 0.0,
        cameraAConfidence = confidence,
        cameraBConfidence = confidence,
        confidence = confidence,
        status = status,
        timestamp = timestamp,
    )
}
