package com.filamentvision.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "error_snapshots",
    foreignKeys = [ForeignKey(
        entity = ErrorEventEntity::class,
        parentColumns = ["id"],
        childColumns = ["errorId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("errorId"), Index("timestamp")],
)
data class ErrorSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val errorId: Long,
    val cameraId: String?,
    val timestamp: Long,
    val filePath: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val mimeType: String,
)

