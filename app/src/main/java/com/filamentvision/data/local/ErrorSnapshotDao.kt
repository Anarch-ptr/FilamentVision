package com.filamentvision.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.filamentvision.data.local.entity.ErrorSnapshotEntity

@Dao
interface ErrorSnapshotDao {
    @Insert suspend fun insert(entity: ErrorSnapshotEntity): Long
    @Query("SELECT * FROM error_snapshots WHERE errorId = :errorId ORDER BY timestamp, id")
    suspend fun getForError(errorId: Long): List<ErrorSnapshotEntity>
    @Query("SELECT MAX(timestamp) FROM error_snapshots WHERE errorId = :errorId")
    suspend fun latestTimestamp(errorId: Long): Long?
    @Query("SELECT COUNT(*) FROM error_snapshots") suspend fun count(): Long
    @Query("SELECT COALESCE(SUM(fileSizeBytes), 0) FROM error_snapshots") suspend fun totalBytes(): Long
    @Query("SELECT MAX(timestamp) FROM error_snapshots") suspend fun lastTimestamp(): Long?
    @Query("SELECT filePath FROM error_snapshots") suspend fun getAllPaths(): List<String>
}
