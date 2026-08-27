package com.filamentvision.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.filamentvision.data.local.entity.AlarmEventEntity
import com.filamentvision.data.local.entity.MeasurementEntity
import com.filamentvision.data.local.entity.MonitoringSessionEntity

@Database(
    entities = [MonitoringSessionEntity::class, MeasurementEntity::class, AlarmEventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FilamentVisionDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun alarmEventDao(): AlarmEventDao

    companion object {
        fun create(context: Context): FilamentVisionDatabase = Room.databaseBuilder(
            context.applicationContext,
            FilamentVisionDatabase::class.java,
            "filamentvision.db",
        ).build()
    }
}
