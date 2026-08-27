package com.filamentvision.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.filamentvision.data.local.entity.AlarmEventEntity

@Dao
interface AlarmEventDao {
    @Insert
    suspend fun insertAll(events: List<AlarmEventEntity>)

    @Query(
        """
        SELECT * FROM alarm_events
        WHERE sessionId = :sessionId AND timestamp BETWEEN :fromTimestamp AND :toTimestamp
        ORDER BY timestamp, id
        """,
    )
    suspend fun getRange(
        sessionId: String,
        fromTimestamp: Long,
        toTimestamp: Long,
    ): List<AlarmEventEntity>

    @Query("SELECT COUNT(*) FROM alarm_events WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: String): Long
}
