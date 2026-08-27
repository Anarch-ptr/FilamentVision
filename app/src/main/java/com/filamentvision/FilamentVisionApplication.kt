package com.filamentvision

import android.app.Application
import com.filamentvision.data.local.FilamentVisionDatabase
import com.filamentvision.data.repository.RoomAlarmRepository
import com.filamentvision.data.repository.RoomMeasurementRepository
import com.filamentvision.data.repository.RoomSessionRepository
import com.filamentvision.data.settings.SharedPreferencesConnectionSettingsRepository
import com.filamentvision.domain.monitoring.MonitoringRuntime
import com.filamentvision.fake.FakeDeviceConnection
import com.filamentvision.fake.FakeVisionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Composition root. It owns one runtime for this application process and no business logic. */
class FilamentVisionApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: FilamentVisionDatabase by lazy { FilamentVisionDatabase.create(this) }
    val sessionRepository by lazy { RoomSessionRepository(database) }
    val measurementRepository by lazy { RoomMeasurementRepository(database.measurementDao()) }
    val alarmRepository by lazy { RoomAlarmRepository(database.alarmEventDao()) }
    val connectionSettingsRepository by lazy { SharedPreferencesConnectionSettingsRepository(this) }
    val monitoringRuntime: MonitoringRuntime by lazy {
        MonitoringRuntime(
            deviceConnection = FakeDeviceConnection(),
            visionSource = FakeVisionSource(),
            sessionRepository = sessionRepository,
            measurementRepository = measurementRepository,
            alarmRepository = alarmRepository,
            dispatcher = Dispatchers.Default,
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { monitoringRuntime.recoverInterruptedSessions() }
    }
}
