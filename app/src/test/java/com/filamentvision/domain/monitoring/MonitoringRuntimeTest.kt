package com.filamentvision.domain.monitoring

import com.filamentvision.data.repository.AlarmRepository
import com.filamentvision.data.repository.MeasurementRepository
import com.filamentvision.data.repository.PersistedAlarmEvent
import com.filamentvision.data.repository.PersistedMeasurement
import com.filamentvision.data.repository.SessionRepository
import com.filamentvision.fake.FakeDeviceConnection
import com.filamentvision.fake.FakeVisionSource
import com.filamentvision.model.ConnectionState
import com.filamentvision.model.MonitoringSession
import com.filamentvision.model.MonitoringState
import com.filamentvision.model.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MonitoringRuntimeTest {
    @Test
    fun readyHasZeroProducerAndDuplicateStartCreatesOneSessionAndProducer() = runTest {
        val fixture = fixture()
        fixture.connect(this)

        assertEquals(ConnectionState.CONNECTED, fixture.runtime.state.value.connectionState)
        assertEquals(MonitoringState.READY, fixture.runtime.state.value.monitoringState)
        assertEquals(0, fixture.source.activeProducerCount)
        assertTrue(fixture.measurements.rows.isEmpty())

        List(10) { async { fixture.runtime.startMonitoring(1.75) } }.awaitAll()
        runCurrent()

        assertEquals(MonitoringState.MONITORING, fixture.runtime.state.value.monitoringState)
        assertEquals(1, fixture.source.activeProducerCount)
        assertEquals(1, fixture.source.totalProducerStarts)
        assertEquals(1, fixture.sessions.created.size)
        fixture.runtime.close()
    }

    @Test
    fun stopIsIdempotentStopsProducerAndFlushesAllAcceptedMeasurements() = runTest {
        val fixture = fixture()
        fixture.connect(this)
        fixture.runtime.startMonitoring(1.75)
        advanceTimeBy(1_150L)
        runCurrent()
        val accepted = fixture.runtime.state.value.statistics.sampleCount

        fixture.runtime.stopMonitoring()
        fixture.runtime.stopMonitoring()

        assertEquals(0, fixture.source.activeProducerCount)
        assertEquals(MonitoringState.COMPLETED, fixture.runtime.state.value.monitoringState)
        assertEquals(accepted, fixture.measurements.rows.size.toLong())
        assertEquals(1, fixture.sessions.finalized.size)
        assertEquals(SessionStatus.COMPLETED, fixture.sessions.finalized.single().status)
        fixture.runtime.close()
    }

    @Test
    fun interruptionUsesSameFinalFlushAndPersistsPartialSession() = runTest {
        val fixture = fixture()
        fixture.connect(this)
        fixture.runtime.startMonitoring(1.75)
        advanceTimeBy(430L)
        runCurrent()
        val accepted = fixture.runtime.state.value.statistics.sampleCount

        fixture.runtime.interruptMonitoring("Connection lost")

        assertEquals(0, fixture.source.activeProducerCount)
        assertEquals(MonitoringState.INTERRUPTED, fixture.runtime.state.value.monitoringState)
        assertEquals(accepted, fixture.measurements.rows.size.toLong())
        assertEquals(SessionStatus.INTERRUPTED, fixture.sessions.finalized.single().status)
        assertEquals("Connection lost", fixture.sessions.finalized.single().endReason)
        fixture.runtime.close()
    }

    @Test
    fun navigationCollectorsNeverStartOrMultiplyProducer() = runTest {
        val fixture = fixture()
        fixture.connect(this)
        val collectors = List(12) { launch { fixture.runtime.state.collect() } }
        runCurrent()

        assertEquals(0, fixture.source.activeProducerCount)
        assertEquals(0, fixture.source.totalProducerStarts)
        fixture.runtime.startMonitoring(1.75)
        runCurrent()

        assertEquals(1, fixture.source.activeProducerCount)
        assertEquals(1, fixture.source.totalProducerStarts)
        collectors.forEach { it.cancel() }
        fixture.runtime.close()
    }

    private fun kotlinx.coroutines.test.TestScope.fixture(): Fixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val source = FakeVisionSource(
            samplePeriodMillis = 125L,
            dispatcher = dispatcher,
            nowMillis = { testScheduler.currentTime },
        )
        val sessions = FakeSessionRepository()
        val measurements = FakeMeasurementRepository()
        return Fixture(
            runtime = MonitoringRuntime(
                deviceConnection = FakeDeviceConnection(),
                visionSource = source,
                sessionRepository = sessions,
                measurementRepository = measurements,
                alarmRepository = FakeAlarmRepository(),
                dispatcher = dispatcher,
                nowMillis = { testScheduler.currentTime },
            ),
            source = source,
            sessions = sessions,
            measurements = measurements,
        )
    }

    private data class Fixture(
        val runtime: MonitoringRuntime,
        val source: FakeVisionSource,
        val sessions: FakeSessionRepository,
        val measurements: FakeMeasurementRepository,
    ) {
        suspend fun connect(scope: kotlinx.coroutines.test.TestScope) = with(scope) {
            val job = launch { runtime.connect() }
            advanceTimeBy(1_200L)
            job.join()
            runCurrent()
        }
    }

    private class FakeSessionRepository : SessionRepository {
        val created = mutableListOf<MonitoringSession>()
        val finalized = mutableListOf<MonitoringSession>()
        private val sessionFlow = MutableStateFlow<List<MonitoringSession>>(emptyList())
        override val sessions: Flow<List<MonitoringSession>> = sessionFlow

        override suspend fun createActive(session: MonitoringSession) {
            created += session
            sessionFlow.value = listOf(session)
        }

        override suspend fun finalize(session: MonitoringSession) {
            finalized += session
            sessionFlow.value = listOf(session)
        }

        override suspend fun getById(sessionId: String) =
            (finalized + created).lastOrNull { it.id == sessionId }
        override suspend fun delete(sessionId: String) = Unit
        override suspend fun recoverInterruptedSessions() = 0
    }

    private class FakeMeasurementRepository : MeasurementRepository {
        val rows = mutableListOf<PersistedMeasurement>()
        override suspend fun insertMeasurements(measurements: List<PersistedMeasurement>) {
            rows += measurements
        }
        override suspend fun getMeasurements(sessionId: String, fromTimestamp: Long, toTimestamp: Long) =
            rows.filter { it.sessionId == sessionId && it.measurement.timestamp in fromTimestamp..toTimestamp }
        override suspend fun getMeasurementPage(
            sessionId: String,
            fromTimestamp: Long,
            toTimestamp: Long,
            afterTimestamp: Long,
            afterId: Long,
            limit: Int,
        ) = getMeasurements(sessionId, fromTimestamp, toTimestamp).take(limit)
        override suspend fun countMeasurements(sessionId: String) = rows.count { it.sessionId == sessionId }.toLong()
    }

    private class FakeAlarmRepository : AlarmRepository {
        val rows = mutableListOf<PersistedAlarmEvent>()
        override suspend fun insertAlarmEvents(events: List<PersistedAlarmEvent>) {
            rows += events
        }
        override suspend fun getAlarmEvents(sessionId: String, fromTimestamp: Long, toTimestamp: Long) =
            rows.filter { it.sessionId == sessionId && it.timestamp in fromTimestamp..toTimestamp }
        override suspend fun countAlarmEvents(sessionId: String) = rows.count { it.sessionId == sessionId }.toLong()
    }
}
