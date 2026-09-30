package com.filamentvision.domain.monitoring

import com.filamentvision.data.repository.AlarmRepository
import com.filamentvision.data.repository.MeasurementRepository
import com.filamentvision.data.repository.PersistedAlarmEvent
import com.filamentvision.data.repository.PersistedMeasurement
import com.filamentvision.data.repository.SessionRepository
import com.filamentvision.hardware.UnavailableDeviceConnection
import com.filamentvision.hardware.UnavailableVisionSource
import com.filamentvision.input.InputState
import com.filamentvision.model.MonitoringSession
import com.filamentvision.model.MonitoringState
import com.filamentvision.model.TransportProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonitoringRuntimeUnavailableTest {
    @Test
    fun unavailableDriverCannotCreateSessionOrProducer() = runTest {
        val sessions = RecordingSessions()
        val inputState = InputState.DriverUnavailable(setOf(TransportProtocol.WIFI_TCP))
        val runtime = MonitoringRuntime(
            deviceConnection = UnavailableDeviceConnection(inputState),
            visionSource = UnavailableVisionSource(inputState),
            sessionRepository = sessions,
            measurementRepository = EmptyMeasurements,
            alarmRepository = EmptyAlarms,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        runtime.connect()
        runtime.startMonitoring(1.75)

        assertEquals(inputState, runtime.state.value.inputState)
        assertEquals(MonitoringState.IDLE, runtime.state.value.monitoringState)
        assertEquals(0, runtime.state.value.activeProducerCount)
        assertNull(runtime.state.value.activeSession)
        assertEquals(0, sessions.created)
        runtime.close()
    }

    private class RecordingSessions : SessionRepository {
        override val sessions: Flow<List<MonitoringSession>> = MutableStateFlow(emptyList())
        var created = 0
        override suspend fun createActive(session: MonitoringSession) { created++ }
        override suspend fun finalize(session: MonitoringSession) = Unit
        override suspend fun getById(sessionId: String): MonitoringSession? = null
        override suspend fun delete(sessionId: String) = Unit
        override suspend fun recoverInterruptedSessions(): Int = 0
    }

    private object EmptyMeasurements : MeasurementRepository {
        override suspend fun insertMeasurements(measurements: List<PersistedMeasurement>) = Unit
        override suspend fun getMeasurements(sessionId: String, fromTimestamp: Long, toTimestamp: Long) = emptyList<PersistedMeasurement>()
        override suspend fun getMeasurementPage(sessionId: String, fromTimestamp: Long, toTimestamp: Long, afterTimestamp: Long, afterId: Long, limit: Int) = emptyList<PersistedMeasurement>()
        override suspend fun countMeasurements(sessionId: String) = 0L
    }

    private object EmptyAlarms : AlarmRepository {
        override suspend fun insertAlarmEvents(events: List<PersistedAlarmEvent>) = Unit
        override suspend fun getAlarmEvents(sessionId: String, fromTimestamp: Long, toTimestamp: Long) = emptyList<PersistedAlarmEvent>()
        override suspend fun countAlarmEvents(sessionId: String) = 0L
    }
}
