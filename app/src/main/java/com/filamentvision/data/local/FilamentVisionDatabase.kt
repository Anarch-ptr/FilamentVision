package com.filamentvision.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.filamentvision.data.local.entity.AlarmEventEntity
import com.filamentvision.data.local.entity.ErrorEventEntity
import com.filamentvision.data.local.entity.ErrorSnapshotEntity
import com.filamentvision.data.local.entity.MeasurementEntity
import com.filamentvision.data.local.entity.MonitoringSessionEntity

@Database(
    entities = [MonitoringSessionEntity::class, MeasurementEntity::class, AlarmEventEntity::class, ErrorEventEntity::class, ErrorSnapshotEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class FilamentVisionDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun alarmEventDao(): AlarmEventDao
    abstract fun errorEventDao(): ErrorEventDao
    abstract fun errorSnapshotDao(): ErrorSnapshotDao

    companion object {
        fun create(context: Context): FilamentVisionDatabase = Room.databaseBuilder(
            context.applicationContext,
            FilamentVisionDatabase::class.java,
            "filamentvision.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `error_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `firstTimestamp` INTEGER NOT NULL, `lastTimestamp` INTEGER NOT NULL, `severity` TEXT NOT NULL, `category` TEXT NOT NULL, `code` TEXT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `component` TEXT, `sessionId` TEXT, `cameraId` TEXT, `recoverable` INTEGER NOT NULL, `state` TEXT NOT NULL, `resolvedAt` INTEGER, `occurrenceCount` INTEGER NOT NULL, `activeIdentityKey` TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_error_events_lastTimestamp` ON `error_events` (`lastTimestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_error_events_state` ON `error_events` (`state`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_error_events_severity` ON `error_events` (`severity`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_error_events_category` ON `error_events` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_error_events_sessionId` ON `error_events` (`sessionId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_error_events_activeIdentityKey` ON `error_events` (`activeIdentityKey`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `error_snapshots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `errorId` INTEGER NOT NULL, `cameraId` TEXT, `timestamp` INTEGER NOT NULL, `filePath` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `fileSizeBytes` INTEGER NOT NULL, `mimeType` TEXT NOT NULL, FOREIGN KEY(`errorId`) REFERENCES `error_events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_error_snapshots_errorId` ON `error_snapshots` (`errorId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_error_snapshots_timestamp` ON `error_snapshots` (`timestamp`)")
            }
        }

        /** One-time cleanup: pre-1.0.3 runtime measurements were generated demo data. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM `alarm_events`")
                db.execSQL("DELETE FROM `measurements`")
                db.execSQL("DELETE FROM `monitoring_sessions`")
                db.execSQL("DELETE FROM `error_snapshots`")
                db.execSQL("DELETE FROM `error_events`")
                db.execSQL("ALTER TABLE `measurements` ADD COLUMN `sourceType` TEXT NOT NULL DEFAULT 'REAL'")
            }
        }
    }
}
