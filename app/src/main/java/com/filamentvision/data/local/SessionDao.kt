package com.filamentvision.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.filamentvision.data.local.entity.MonitoringSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: MonitoringSessionEntity)

    @Update
    suspend fun update(session: MonitoringSessionEntity)

    @Query("SELECT * FROM monitoring_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<MonitoringSessionEntity>>

    @Query("SELECT * FROM monitoring_sessions WHERE sessionId = :sessionId")
    suspend fun getById(sessionId: String): MonitoringSessionEntity?

    @Query("DELETE FROM monitoring_sessions WHERE sessionId = :sessionId")
    suspend fun deleteById(sessionId: String)

    @Query(
        """
        UPDATE monitoring_sessions
        SET status = 'INTERRUPTED',
            endedAt = COALESCE(
                (SELECT MAX(timestamp) FROM measurements
                 WHERE measurements.sessionId = monitoring_sessions.sessionId),
                startedAt
            ),
            endReason = 'PROCESS_TERMINATED'
        WHERE status = 'ACTIVE'
        """,
    )
    suspend fun recoverActiveSessions(): Int
}
