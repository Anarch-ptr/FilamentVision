package com.filamentvision.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "error_events",
    indices = [
        Index("lastTimestamp"), Index("state"), Index("severity"), Index("category"), Index("sessionId"),
        Index(value = ["activeIdentityKey"], unique = true),
    ],
)
data class ErrorEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val firstTimestamp: Long,
    val lastTimestamp: Long,
    val severity: String,
    val category: String,
    val code: String,
    val title: String,
    val message: String,
    val component: String?,
    val sessionId: String?,
    val cameraId: String?,
    val recoverable: Boolean,
    val state: String,
    val resolvedAt: Long?,
    val occurrenceCount: Int,
    /** Stable while ACTIVE and cleared on resolution, giving SQLite-level single-episode enforcement. */
    val activeIdentityKey: String?,
)
