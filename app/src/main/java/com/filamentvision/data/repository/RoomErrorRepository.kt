package com.filamentvision.data.repository

import androidx.room.withTransaction
import android.util.Log
import com.filamentvision.data.local.FilamentVisionDatabase
import com.filamentvision.data.local.entity.ErrorEventEntity
import com.filamentvision.data.local.entity.ErrorSnapshotEntity
import com.filamentvision.domain.error.ErrorCategory
import com.filamentvision.domain.error.ErrorEpisodeIdentity
import com.filamentvision.domain.error.ErrorEvent
import com.filamentvision.domain.error.ErrorSeverity
import com.filamentvision.domain.error.ErrorSnapshot
import com.filamentvision.domain.error.ErrorState
import com.filamentvision.domain.error.NewErrorRecord
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomErrorRepository(private val database: FilamentVisionDatabase) : ErrorRepository {
    private val errorsDao = database.errorEventDao()
    private val snapshotsDao = database.errorSnapshotDao()
    private val episodeMutex = Mutex()
    override val errors: Flow<List<ErrorEvent>> = errorsDao.observeAll().map { it.map(ErrorEventEntity::toDomain) }

    override suspend fun record(record: NewErrorRecord, timestamp: Long): ErrorRecordResult = episodeMutex.withLock {
        database.withTransaction {
            val key = record.identity
            val active = errorsDao.findActive(key.code, key.component, key.cameraId, key.sessionId)
            if (active == null) {
                val id = errorsDao.insert(record.toEntity(timestamp))
                ErrorRecordResult(checkNotNull(errorsDao.getById(id)).toDomain(), true)
            } else {
                errorsDao.touch(active.id, timestamp, record.severity.name, record.title, record.message)
                ErrorRecordResult(checkNotNull(errorsDao.getById(active.id)).toDomain(), false)
            }
        }
    }

    override suspend fun resolve(identity: ErrorEpisodeIdentity, timestamp: Long): Boolean = episodeMutex.withLock {
        errorsDao.resolve(identity.code, identity.component, identity.cameraId, identity.sessionId, timestamp) > 0
    }

    override suspend fun resolveMatching(code: String, component: String?, cameraId: String?, timestamp: Long): Boolean = episodeMutex.withLock {
        errorsDao.resolveMatching(code, component, cameraId, timestamp) > 0
    }

    override suspend fun getById(id: Long) = errorsDao.getById(id)?.toDomain()
    override suspend fun getForSession(sessionId: String) = errorsDao.getForSession(sessionId).map(ErrorEventEntity::toDomain)
    override suspend fun getSnapshots(errorId: Long) = snapshotsDao.getForError(errorId).map(ErrorSnapshotEntity::toDomain)
    override suspend fun latestSnapshotTimestamp(errorId: Long) = snapshotsDao.latestTimestamp(errorId)
    override suspend fun addSnapshot(snapshot: ErrorSnapshot): ErrorSnapshot {
        val id = snapshotsDao.insert(snapshot.toEntity())
        return snapshot.copy(id = id)
    }

    override suspend fun deleteResolved(id: Long): Boolean {
        val paths = snapshotsDao.getForError(id).map { it.filePath }
        val deleted = database.withTransaction { errorsDao.deleteResolvedById(id) > 0 }
        if (deleted) paths.forEach(::deleteSnapshotFile)
        return deleted
    }

    override suspend fun clearResolved(): Int {
        val resolved = errorsDao.getResolved()
        val paths = resolved.flatMap { snapshotsDao.getForError(it.id) }.map { it.filePath }
        val deleted = database.withTransaction { errorsDao.clearResolved() }
        paths.forEach(::deleteSnapshotFile)
        return deleted
    }

    override suspend fun snapshotDiagnostics() = ErrorSnapshotDiagnostics(
        count = snapshotsDao.count(), totalBytes = snapshotsDao.totalBytes(), lastTimestamp = snapshotsDao.lastTimestamp(),
    )

    override suspend fun reconcileSnapshotFiles(directory: File): Int {
        val referenced = snapshotsDao.getAllPaths().mapTo(HashSet()) { File(it).absolutePath }
        var removed = 0
        directory.listFiles()?.filter(File::isFile)?.forEach { file ->
            if (file.absolutePath !in referenced && file.delete()) removed++
        }
        return removed
    }

    private fun deleteSnapshotFile(path: String) {
        val file = File(path)
        if (file.exists() && !file.delete()) Log.w(TAG, "Could not delete snapshot ${file.absolutePath}")
    }

    private companion object { const val TAG = "RoomErrorRepository" }
}

private fun NewErrorRecord.toEntity(timestamp: Long) = ErrorEventEntity(
    firstTimestamp = timestamp, lastTimestamp = timestamp, severity = severity.name, category = category.name,
    code = code, title = title, message = message, component = component, sessionId = sessionId,
    cameraId = cameraId, recoverable = recoverable, state = ErrorState.ACTIVE.name, resolvedAt = null, occurrenceCount = 1,
    activeIdentityKey = identity.activeKey(),
)
private fun ErrorEpisodeIdentity.activeKey() = listOf(code, component.orEmpty(), cameraId.orEmpty(), sessionId.orEmpty()).joinToString("\u001f")
private fun ErrorEventEntity.toDomain() = ErrorEvent(
    id, firstTimestamp, lastTimestamp, ErrorSeverity.valueOf(severity), ErrorCategory.valueOf(category), code, title,
    message, component, sessionId, cameraId, recoverable, ErrorState.valueOf(state), resolvedAt, occurrenceCount,
)
private fun ErrorSnapshotEntity.toDomain() = ErrorSnapshot(id, errorId, cameraId, timestamp, filePath, width, height, fileSizeBytes, mimeType)
private fun ErrorSnapshot.toEntity() = ErrorSnapshotEntity(id, errorId, cameraId, timestamp, filePath, width, height, fileSizeBytes, mimeType)
