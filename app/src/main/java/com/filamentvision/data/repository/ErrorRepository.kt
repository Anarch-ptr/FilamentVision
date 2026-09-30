package com.filamentvision.data.repository

import com.filamentvision.domain.error.ErrorEpisodeIdentity
import com.filamentvision.domain.error.ErrorEvent
import com.filamentvision.domain.error.ErrorSnapshot
import com.filamentvision.domain.error.NewErrorRecord
import kotlinx.coroutines.flow.Flow
import java.io.File

data class ErrorRecordResult(val event: ErrorEvent, val isNewEpisode: Boolean)
data class ErrorSnapshotDiagnostics(val count: Long = 0, val totalBytes: Long = 0, val lastTimestamp: Long? = null)

interface ErrorRepository {
    val errors: Flow<List<ErrorEvent>>
    suspend fun record(record: NewErrorRecord, timestamp: Long): ErrorRecordResult
    suspend fun resolve(identity: ErrorEpisodeIdentity, timestamp: Long): Boolean
    suspend fun resolveMatching(code: String, component: String?, cameraId: String?, timestamp: Long): Boolean
    suspend fun getById(id: Long): ErrorEvent?
    suspend fun getForSession(sessionId: String): List<ErrorEvent>
    suspend fun getSnapshots(errorId: Long): List<ErrorSnapshot>
    suspend fun latestSnapshotTimestamp(errorId: Long): Long?
    suspend fun addSnapshot(snapshot: ErrorSnapshot): ErrorSnapshot
    suspend fun deleteResolved(id: Long): Boolean
    suspend fun clearResolved(): Int
    suspend fun snapshotDiagnostics(): ErrorSnapshotDiagnostics
    suspend fun reconcileSnapshotFiles(directory: File): Int
}
