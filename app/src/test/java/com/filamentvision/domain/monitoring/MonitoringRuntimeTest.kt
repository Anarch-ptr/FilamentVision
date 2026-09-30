package com.filamentvision.domain.monitoring

import com.filamentvision.data.repository.AlarmRepository
import com.filamentvision.data.repository.MeasurementRepository
import com.filamentvision.data.repository.PersistedAlarmEvent
import com.filamentvision.data.repository.PersistedMeasurement
import com.filamentvision.data.repository.SessionRepository
import com.filamentvision.domain.error.ErrorEpisodeIdentity
import com.filamentvision.domain.error.ErrorEvent
import com.filamentvision.domain.error.ErrorRecorder
import com.filamentvision.domain.error.NewErrorRecord
import com.filamentvision.fake.FakeDeviceConnection
import com.filamentvision.fake.FakeVisionSource
import com.filamentvision.model.ConnectionState
import com.filamentvision.model.MonitoringSession
import com.filamentvision.model.MonitoringState
import com.filamentvision.model.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
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
    fun concurrentLazyRecoveryTouchesRepositoryExactlyOnce() = runTest {
        val fixture = fixture()

        val results = List(20) { async { fixture.runtime.recoverInterruptedSessions() } }.awaitAll()

        assertEquals(List(20) { 7 }, results)
        assertEquals(1, fixture.sessions.recoveryCalls)
        fixture.runtime.close()
    }

    @Test
    fun firstLifecycleCommandPerformsRecoveryBeforeConnectionIsReady() = runTest {
        val fixture = fixture()

        fixture.connect(this)

        assertEquals(1, fixture.sessions.recoveryCalls)
        assertEquals(ConnectionState.CONNECTED, fixture.runtime.state.value.connectionState)
        fixture.runtime.close()
    }

    @Test
    fun repositoryFactoriesStayLazyUntilTheirRuntimePathIsUsed() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sessions = FakeSessionRepository()
        val measurements = FakeMeasurementRepository()
        val alarms = FakeAlarmRepository()
        var sessionFactoryCalls = 0
        var measurementFactoryCalls = 0
        var alarmFactoryCalls = 0
        val source = FakeVisionSource(dispatcher = dispatcher)
        val runtime = MonitoringRuntime(
            deviceConnection = FakeDeviceConnection(),
            visionSource = source,
            sessionRepositoryProvider = { sessionFactoryCalls += 1; sessions },
            measurementRepositoryProvider = { measurementFactoryCalls += 1; measurements },
            alarmRepositoryProvider = { alarmFactoryCalls += 1; alarms },
            dispatcher = dispatcher,
        )

        assertEquals(0, sessionFactoryCalls)
        assertEquals(0, measurementFactoryCalls)
        assertEquals(0, alarmFactoryCalls)

        val connectJob = launch { runtime.connect() }
        advanceTimeBy(1_250L)
        connectJob.join()
        assertEquals(1, sessionFactoryCalls)
        assertEquals(0, measurementFactoryCalls)
        assertEquals(0, alarmFactoryCalls)

        runtime.startMonitoring(1.75)
        runCurrent()
        assertEquals(1, sessionFactoryCalls)
        assertEquals(1, measurementFactoryCalls)
        assertEquals(1, alarmFactoryCalls)
        runtime.close()
    }

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
        val errors = FakeErrorRecorder()
        return Fixture(
            runtime = MonitoringRuntime(
                deviceConnection = FakeDeviceConnection(),
                visionSource = source,
                sessionRepository = sessions,
                measurementRepository = measurements,
                alarmRepository = FakeAlarmRepository(),
                errorRecorder = errors,
                dispatcher = dispatcher,
                nowMillis = { testScheduler.currentTime },
            ),
            source = source,
            sessions = sessions,
            measurements = measurements,
            errors = errors,
        )
    }

    private data class Fixture(
        val runtime: MonitoringRuntime,
        val source: FakeVisionSource,
        val sessions: FakeSessionRepository,
        val measurements: FakeMeasurementRepository,
        val errors: FakeErrorRecorder,
    ) {
        suspend fun connect(scope: kotlinx.coroutines.test.TestScope) = with(scope) {
            val job = launch { runtime.connect() }
            advanceTimeBy(1_200L)
            job.join()
            runCurrent()
        }
    }

    private class FakeErrorRecorder : ErrorRecorder {
        val records = mutableListOf<NewErrorRecord>()
        val resolutions = mutableListOf<ErrorEpisodeIdentity>()
        override suspend fun record(record: NewErrorRecord): ErrorEvent? { records += record; return null }
        override suspend fun resolve(identity: ErrorEpisodeIdentity): Boolean { resolutions += identity; return true }
    }

    private class FakeSessionRepository : SessionRepository {
        val created = mutableListOf<MonitoringSession>()
        val finalized = mutableListOf<MonitoringSession>()
        var recoveryCalls = 0
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
        override suspend fun recoverInterruptedSessions(): Int {
            recoveryCalls += 1
            delay(50L)
            return 7
        }
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
