package com.filamentvision.domain.error

enum class ErrorSeverity { INFO, WARNING, ERROR, CRITICAL }
enum class ErrorCategory { CONNECTION, CAMERA, MONITORING, SERVICE, DATABASE, SESSION, VISUAL_PROCESSING, RECOVERY, UNKNOWN }
enum class ErrorState { ACTIVE, RESOLVED }

data class ErrorEpisodeIdentity(
    val code: String,
    val component: String?,
    val cameraId: String?,
    val sessionId: String?,
)

data class NewErrorRecord(
    val severity: ErrorSeverity,
    val category: ErrorCategory,
    val code: String,
    val title: String,
    val message: String,
    val component: String? = null,
    val sessionId: String? = null,
    val cameraId: String? = null,
    val recoverable: Boolean = true,
) {
    val identity: ErrorEpisodeIdentity get() = ErrorEpisodeIdentity(code, component, cameraId, sessionId)

    companion object {
        fun cameraFailure(cameraId: String, sessionId: String?) = NewErrorRecord(
            severity = ErrorSeverity.ERROR,
            category = ErrorCategory.CAMERA,
            code = "CAMERA_FAILURE",
            title = "Camera ${cameraId.uppercase()} unavailable",
            message = "Camera ${cameraId.uppercase()} failed to provide a valid source.",
            component = "Camera",
            sessionId = sessionId,
            cameraId = cameraId.uppercase(),
        )
    }
}

data class ErrorEvent(
    val id: Long,
    val firstTimestamp: Long,
    val lastTimestamp: Long,
    val severity: ErrorSeverity,
    val category: ErrorCategory,
    val code: String,
    val title: String,
    val message: String,
    val component: String?,
    val sessionId: String?,
    val cameraId: String?,
    val recoverable: Boolean,
    val state: ErrorState,
    val resolvedAt: Long?,
    val occurrenceCount: Int,
) {
    val identity: ErrorEpisodeIdentity get() = ErrorEpisodeIdentity(code, component, cameraId, sessionId)
}

data class ErrorSnapshot(
    val id: Long,
    val errorId: Long,
    val cameraId: String?,
    val timestamp: Long,
    val filePath: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val mimeType: String,
)

object ErrorSnapshotPolicy {
    const val COOLDOWN_MS = 5 * 60 * 1_000L
    const val JPEG_QUALITY = 80

    fun shouldCapture(isNewEpisode: Boolean, lastSnapshotAt: Long?, now: Long): Boolean =
        isNewEpisode || lastSnapshotAt == null || now - lastSnapshotAt >= COOLDOWN_MS
}

