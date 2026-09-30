package com.filamentvision.domain.error

import android.util.Log
import com.filamentvision.camera.snapshot.ErrorSnapshotSource
import com.filamentvision.data.repository.ErrorRepository
import com.filamentvision.storage.ErrorSnapshotFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ErrorRecorder {
    suspend fun record(record: NewErrorRecord): ErrorEvent?
    suspend fun resolve(identity: ErrorEpisodeIdentity): Boolean
    suspend fun resolveMatching(code: String, component: String?, cameraId: String?): Boolean = false
}

object NoOpErrorRecorder : ErrorRecorder {
    override suspend fun record(record: NewErrorRecord): ErrorEvent? = null
    override suspend fun resolve(identity: ErrorEpisodeIdentity) = false
    override suspend fun resolveMatching(code: String, component: String?, cameraId: String?) = false
}

class PersistentErrorRecorder(
    private val repository: ErrorRepository,
    private val snapshotSource: ErrorSnapshotSource,
    private val fileStore: ErrorSnapshotFileStore,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ErrorRecorder {
    private val snapshotMutex = Mutex()

    override suspend fun record(record: NewErrorRecord): ErrorEvent? {
        val timestamp = nowMillis()
        val result = try { repository.record(record, timestamp) } catch (failure: Throwable) {
            Log.e(TAG, "Persistent error recording failed for ${record.code}", failure)
            return null
        }
        if (record.cameraId != null && record.category in SNAPSHOT_CATEGORIES) {
            scope.launch { captureSnapshot(result.event, result.isNewEpisode, timestamp) }
        }
        return result.event
    }

    override suspend fun resolve(identity: ErrorEpisodeIdentity): Boolean = try {
        repository.resolve(identity, nowMillis())
    } catch (failure: Throwable) {
        Log.e(TAG, "Persistent error resolution failed for ${identity.code}", failure)
        false
    }

    override suspend fun resolveMatching(code: String, component: String?, cameraId: String?): Boolean = try {
        repository.resolveMatching(code, component, cameraId, nowMillis())
    } catch (failure: Throwable) {
        Log.e(TAG, "Persistent matching error resolution failed for $code", failure)
        false
    }

    private suspend fun captureSnapshot(event: ErrorEvent, isNewEpisode: Boolean, timestamp: Long) {
        snapshotMutex.withLock {
            try {
                val last = repository.latestSnapshotTimestamp(event.id)
                if (!ErrorSnapshotPolicy.shouldCapture(isNewEpisode && last == null, last, timestamp)) return@withLock
                val cameraId = event.cameraId ?: return@withLock
                val frame = snapshotSource.getLatestFrame(cameraId) ?: return@withLock
                val saved = fileStore.save(event.id, cameraId, timestamp, frame)
                try {
                    repository.addSnapshot(ErrorSnapshot(0L, event.id, cameraId, timestamp, saved.path, saved.width, saved.height, saved.sizeBytes, saved.mimeType))
                } catch (failure: Throwable) {
                    if (!fileStore.delete(saved.path)) Log.w(TAG, "Could not remove orphan snapshot ${saved.path}")
                    throw failure
                }
            } catch (failure: Throwable) {
                Log.w(TAG, "Snapshot unavailable for error ${event.id}", failure)
            }
        }
    }

    private companion object {
        const val TAG = "ErrorRecorder"
        val SNAPSHOT_CATEGORIES = setOf(ErrorCategory.CAMERA, ErrorCategory.VISUAL_PROCESSING)
    }
}
