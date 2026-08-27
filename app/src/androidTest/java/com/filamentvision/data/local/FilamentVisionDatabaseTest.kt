package com.filamentvision.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.filamentvision.data.local.entity.AlarmEventEntity
import com.filamentvision.data.local.entity.MeasurementEntity
import com.filamentvision.data.local.entity.MonitoringSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilamentVisionDatabaseTest {
    private lateinit var database: FilamentVisionDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            FilamentVisionDatabase::class.java,
        ).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun rangeQueriesKeepSessionsIsolatedAndOrdered() = runBlocking {
        database.sessionDao().insert(session("A"))
        database.sessionDao().insert(session("B"))
        database.measurementDao().insertAll(
            listOf(
                measurement("A", timestamp = 300L, diameter = 1.77),
                measurement("B", timestamp = 200L, diameter = 1.80),
                measurement("A", timestamp = 100L, diameter = 1.75),
            ),
        )

        val result = database.measurementDao().getRange("A", 0L, 500L)

        assertEquals(listOf(100L, 300L), result.map(MeasurementEntity::timestamp))
        assertEquals(listOf(1.75, 1.77), result.map(MeasurementEntity::fusedDiameter))
    }

    @Test
    fun deletingSessionCascadesMeasurementsAndAlarmEvents() = runBlocking {
        database.sessionDao().insert(session("A"))
        database.measurementDao().insertAll(listOf(measurement("A", 100L, 1.80)))
        database.alarmEventDao().insertAll(
            listOf(
                AlarmEventEntity(
                    sessionId = "A",
                    timestamp = 100L,
                    fromLevel = "NORMAL",
                    toLevel = "WARNING",
                    direction = "HIGH",
                    fusedDiameter = 1.80,
                    deviationPercent = 2.86,
                    description = "Warning started",
                ),
            ),
        )

        database.sessionDao().deleteById("A")

        assertEquals(0L, database.measurementDao().countForSession("A"))
        assertEquals(0L, database.alarmEventDao().countForSession("A"))
        assertNull(database.sessionDao().getById("A"))
    }

    @Test
    fun recoveryInterruptsActiveSessionAtLastPersistedMeasurement() = runBlocking {
        database.sessionDao().insert(session("A"))
        database.measurementDao().insertAll(
            listOf(measurement("A", 125L, 1.75), measurement("A", 250L, 1.76)),
        )

        assertEquals(1, database.sessionDao().recoverActiveSessions())
        val recovered = database.sessionDao().getById("A")

        assertEquals("INTERRUPTED", recovered?.status)
        assertEquals("PROCESS_TERMINATED", recovered?.endReason)
        assertEquals(250L, recovered?.endedAt)
    }

    private fun session(id: String) = MonitoringSessionEntity(
        sessionId = id,
        startedAt = 50L,
        endedAt = null,
        targetDiameter = 1.75,
        warningThresholdPercent = 0.02,
        criticalThresholdPercent = 0.05,
        status = "ACTIVE",
        endReason = null,
        sampleCount = 0L,
        averageDiameter = 0.0,
        minimumDiameter = 0.0,
        maximumDiameter = 0.0,
        standardDeviation = 0.0,
        averageConfidence = 0.0,
        abnormalEventCount = 0,
    )

    private fun measurement(sessionId: String, timestamp: Long, diameter: Double) = MeasurementEntity(
        sessionId = sessionId,
        timestamp = timestamp,
        diameterA = diameter,
        diameterB = diameter,
        fusedDiameter = diameter,
        shapeDifference = 0.0,
        cameraAConfidence = 0.98,
        cameraBConfidence = 0.97,
        confidence = 0.975,
        measurementStatus = "NORMAL",
    )
}
