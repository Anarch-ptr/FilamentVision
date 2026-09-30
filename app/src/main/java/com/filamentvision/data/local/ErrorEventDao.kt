package com.filamentvision.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.filamentvision.data.local.entity.ErrorEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ErrorEventDao {
    @Query("SELECT * FROM error_events ORDER BY lastTimestamp DESC, id DESC")
    fun observeAll(): Flow<List<ErrorEventEntity>>

    @Query("SELECT * FROM error_events WHERE id = :id")
    suspend fun getById(id: Long): ErrorEventEntity?

    @Query("SELECT * FROM error_events WHERE sessionId = :sessionId ORDER BY firstTimestamp, id")
    suspend fun getForSession(sessionId: String): List<ErrorEventEntity>

    @Query("SELECT * FROM error_events WHERE state = 'ACTIVE' AND code = :code AND component IS :component AND cameraId IS :cameraId AND sessionId IS :sessionId LIMIT 1")
    suspend fun findActive(code: String, component: String?, cameraId: String?, sessionId: String?): ErrorEventEntity?

    @Insert
    suspend fun insert(entity: ErrorEventEntity): Long

    @Query("""UPDATE error_events SET lastTimestamp = MAX(lastTimestamp, :timestamp), occurrenceCount = occurrenceCount + 1,
        severity = CASE WHEN (CASE :severity WHEN 'CRITICAL' THEN 4 WHEN 'ERROR' THEN 3 WHEN 'WARNING' THEN 2 ELSE 1 END) >
        (CASE severity WHEN 'CRITICAL' THEN 4 WHEN 'ERROR' THEN 3 WHEN 'WARNING' THEN 2 ELSE 1 END) THEN :severity ELSE severity END,
        title = :title, message = :message WHERE id = :id AND state = 'ACTIVE'""")
    suspend fun touch(id: Long, timestamp: Long, severity: String, title: String, message: String): Int

    @Query("UPDATE error_events SET state = 'RESOLVED', resolvedAt = :timestamp, lastTimestamp = MAX(lastTimestamp, :timestamp), activeIdentityKey = NULL WHERE state = 'ACTIVE' AND code = :code AND component IS :component AND cameraId IS :cameraId AND sessionId IS :sessionId")
    suspend fun resolve(code: String, component: String?, cameraId: String?, sessionId: String?, timestamp: Long): Int

    @Query("UPDATE error_events SET state = 'RESOLVED', resolvedAt = :timestamp, lastTimestamp = MAX(lastTimestamp, :timestamp), activeIdentityKey = NULL WHERE state = 'ACTIVE' AND code = :code AND component IS :component AND cameraId IS :cameraId")
    suspend fun resolveMatching(code: String, component: String?, cameraId: String?, timestamp: Long): Int

    @Query("SELECT * FROM error_events WHERE state = 'RESOLVED'")
    suspend fun getResolved(): List<ErrorEventEntity>

    @Query("DELETE FROM error_events WHERE id = :id AND state = 'RESOLVED'")
    suspend fun deleteResolvedById(id: Long): Int

    @Query("DELETE FROM error_events WHERE state = 'RESOLVED'")
    suspend fun clearResolved(): Int
}
