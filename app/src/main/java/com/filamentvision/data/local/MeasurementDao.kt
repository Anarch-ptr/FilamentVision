package com.filamentvision.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.filamentvision.data.local.entity.MeasurementEntity

@Dao
interface MeasurementDao {
    @Insert
    suspend fun insertAll(measurements: List<MeasurementEntity>)

    @Query(
        """
        SELECT * FROM measurements
        WHERE sessionId = :sessionId AND timestamp BETWEEN :fromTimestamp AND :toTimestamp
        ORDER BY timestamp, id
        """,
    )
    suspend fun getRange(
        sessionId: String,
        fromTimestamp: Long,
        toTimestamp: Long,
    ): List<MeasurementEntity>

    @Query(
        """
        SELECT * FROM measurements
        WHERE sessionId = :sessionId
          AND timestamp BETWEEN :fromTimestamp AND :toTimestamp
          AND (timestamp > :afterTimestamp OR (timestamp = :afterTimestamp AND id > :afterId))
        ORDER BY timestamp, id
        LIMIT :limit
        """,
    )
    suspend fun getPage(
        sessionId: String,
        fromTimestamp: Long,
        toTimestamp: Long,
        afterTimestamp: Long,
        afterId: Long,
        limit: Int,
    ): List<MeasurementEntity>

    @Query("SELECT COUNT(*) FROM measurements WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: String): Long
}
