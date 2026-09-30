package com.filamentvision.data.local

import android.content.Context
import androidx.room.Room
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.filamentvision.data.local.entity.AlarmEventEntity
import com.filamentvision.data.local.entity.MeasurementEntity
import com.filamentvision.data.local.entity.MonitoringSessionEntity
import com.filamentvision.data.local.entity.ErrorEventEntity
import com.filamentvision.data.local.entity.ErrorSnapshotEntity
import com.filamentvision.data.repository.RoomErrorRepository
import com.filamentvision.domain.error.NewErrorRecord
import com.filamentvision.domain.error.ErrorSnapshot
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    @Test
    fun deletingErrorCascadesSnapshotMetadata() = runBlocking {
        val errorId = database.errorEventDao().insert(
            ErrorEventEntity(
                firstTimestamp = 100L,
                lastTimestamp = 100L,
                severity = "ERROR",
                category = "CAMERA",
                code = "CAMERA_FAILURE",
                title = "Camera A unavailable",
                message = "Camera A failed",
                component = "Camera",
                sessionId = null,
                cameraId = "A",
                recoverable = true,
                state = "RESOLVED",
                resolvedAt = 200L,
                occurrenceCount = 1,
                activeIdentityKey = null,
            ),
        )
        database.errorSnapshotDao().insert(
            ErrorSnapshotEntity(
                errorId = errorId,
                cameraId = "A",
                timestamp = 100L,
                filePath = "files/error_snapshots/a.jpg",
                width = 192,
                height = 144,
                fileSizeBytes = 1234L,
                mimeType = "image/jpeg",
            ),
        )

        database.errorEventDao().deleteResolvedById(errorId)

        assertEquals(0, database.errorSnapshotDao().getForError(errorId).size)
    }

    @Test
    fun repeatedActiveFaultUpdatesOneEpisodeAndResolvedRecurrenceCreatesAnother() = runBlocking {
        val repository = RoomErrorRepository(database)
        val record = NewErrorRecord.cameraFailure("A", "session-1")

        val first = repository.record(record, 100L)
        val repeated = repository.record(record, 200L)

        assertEquals(first.event.id, repeated.event.id)
        assertEquals(2, repeated.event.occurrenceCount)
        assertEquals(200L, repeated.event.lastTimestamp)

        repository.resolve(record.identity, 300L)
        val recurrence = repository.record(record, 400L)
        assertEquals(false, recurrence.event.id == first.event.id)
        assertEquals(1, recurrence.event.occurrenceCount)
    }

    @Test
    fun cameraIdsCreateIndependentEpisodes() = runBlocking {
        val repository = RoomErrorRepository(database)
        val cameraA = repository.record(NewErrorRecord.cameraFailure("A", null), 100L)
        val cameraB = repository.record(NewErrorRecord.cameraFailure("B", null), 100L)
        assertEquals(false, cameraA.event.id == cameraB.event.id)
        assertEquals("A", cameraA.event.cameraId)
        assertEquals("B", cameraB.event.cameraId)
    }

    @Test
    fun concurrentRecordersStillCreateOneActiveEpisode() = runBlocking {
        val repositories = listOf(RoomErrorRepository(database), RoomErrorRepository(database))
        val record = NewErrorRecord.cameraFailure("A", "session-concurrent")

        val results = coroutineScope {
            List(100) { index -> async(Dispatchers.Default) { repositories[index % 2].record(record, 1_000L + index) } }.awaitAll()
        }

        assertEquals(1, results.map { it.event.id }.distinct().size)
        assertEquals(100, database.errorEventDao().getById(results.first().event.id)?.occurrenceCount)
    }

    @Test
    fun stableConditionResolutionWorksAcrossRepositoryInstancesAndSessionIds() = runBlocking {
        val firstProcess = RoomErrorRepository(database)
        val active = firstProcess.record(NewErrorRecord.cameraFailure("A", "old-session"), 100L)

        val restartedProcess = RoomErrorRepository(database)
        assertEquals(true, restartedProcess.resolveMatching("CAMERA_FAILURE", "Camera", "A", 200L))

        assertEquals("RESOLVED", database.errorEventDao().getById(active.event.id)?.state)
        val recurrence = restartedProcess.record(NewErrorRecord.cameraFailure("A", "new-session"), 300L)
        assertEquals(false, recurrence.event.id == active.event.id)
    }

    @Test
    fun migrationFromVersionOnePerformsOneTimeFakeHistoryCleanupAndCreatesV3Schema() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${System.nanoTime()}.db"
        context.deleteDatabase(name)
        val legacy = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        createVersionOneSchema(legacy)
        legacy.execSQL("INSERT INTO monitoring_sessions VALUES ('legacy', 10, NULL, 1.75, 0.02, 0.05, 'ACTIVE', NULL, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)")
        legacy.version = 1
        legacy.close()

        val migrated = Room.databaseBuilder(context, FilamentVisionDatabase::class.java, name)
            .addMigrations(FilamentVisionDatabase.MIGRATION_1_2, FilamentVisionDatabase.MIGRATION_2_3).build()
        try {
            assertNull(migrated.sessionDao().getById("legacy"))
            val recorded = RoomErrorRepository(migrated).record(NewErrorRecord.cameraFailure("A", "legacy"), 100L)
            assertEquals("CAMERA_FAILURE", recorded.event.code)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun resolvedDeletionRemovesSnapshotFileAndReconciliationRemovesOrphans() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.filesDir, "error_snapshots").apply { mkdirs() }
        val referencedFile = File(directory, "delete-me-${System.nanoTime()}.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val orphanFile = File(directory, "orphan-${System.nanoTime()}.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val repository = RoomErrorRepository(database)
        val record = NewErrorRecord.cameraFailure("A", null)
        val event = repository.record(record, 100L).event
        repository.addSnapshot(ErrorSnapshot(0L, event.id, "A", 100L, referencedFile.absolutePath, 1, 1, 3L, "image/jpeg"))
        repository.resolve(record.identity, 200L)

        assertEquals(true, repository.deleteResolved(event.id))
        assertEquals(false, referencedFile.exists())
        assertEquals(1, repository.reconcileSnapshotFiles(directory))
        assertEquals(false, orphanFile.exists())
    }

    private fun createVersionOneSchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE monitoring_sessions (sessionId TEXT NOT NULL PRIMARY KEY, startedAt INTEGER NOT NULL, endedAt INTEGER, targetDiameter REAL NOT NULL, warningThresholdPercent REAL NOT NULL, criticalThresholdPercent REAL NOT NULL, status TEXT NOT NULL, endReason TEXT, sampleCount INTEGER NOT NULL, averageDiameter REAL NOT NULL, minimumDiameter REAL NOT NULL, maximumDiameter REAL NOT NULL, standardDeviation REAL NOT NULL, averageConfidence REAL NOT NULL, abnormalEventCount INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX index_monitoring_sessions_status ON monitoring_sessions(status)")
        db.execSQL("CREATE INDEX index_monitoring_sessions_startedAt ON monitoring_sessions(startedAt)")
        db.execSQL("CREATE TABLE measurements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId TEXT NOT NULL, timestamp INTEGER NOT NULL, diameterA REAL NOT NULL, diameterB REAL NOT NULL, fusedDiameter REAL NOT NULL, shapeDifference REAL NOT NULL, cameraAConfidence REAL NOT NULL, cameraBConfidence REAL NOT NULL, confidence REAL NOT NULL, measurementStatus TEXT NOT NULL, FOREIGN KEY(sessionId) REFERENCES monitoring_sessions(sessionId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_measurements_sessionId_timestamp ON measurements(sessionId, timestamp)")
        db.execSQL("CREATE TABLE alarm_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId TEXT NOT NULL, timestamp INTEGER NOT NULL, fromLevel TEXT NOT NULL, toLevel TEXT NOT NULL, direction TEXT NOT NULL, fusedDiameter REAL NOT NULL, deviationPercent REAL NOT NULL, description TEXT NOT NULL, FOREIGN KEY(sessionId) REFERENCES monitoring_sessions(sessionId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_alarm_events_sessionId_timestamp ON alarm_events(sessionId, timestamp)")
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
