package com.filamentvision

import android.app.Application
import com.filamentvision.camera.snapshot.LatestCameraFrameCache
import com.filamentvision.data.local.FilamentVisionDatabase
import com.filamentvision.data.repository.RoomAlarmRepository
import com.filamentvision.data.repository.RoomErrorRepository
import com.filamentvision.data.repository.RoomMeasurementRepository
import com.filamentvision.data.repository.RoomSessionRepository
import com.filamentvision.data.settings.SharedPreferencesConnectionSettingsRepository
import com.filamentvision.domain.error.PersistentErrorRecorder
import com.filamentvision.domain.monitoring.MonitoringRuntime
import com.filamentvision.hardware.UnavailableDeviceConnection
import com.filamentvision.hardware.PipelineVisionSource
import com.filamentvision.input.ImageInputSourceFactory
import com.filamentvision.storage.ErrorSnapshotFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.filamentvision.vision.runtime.VisionPipelineRuntime
import java.io.File

/** Composition root. It owns one runtime for this application process and no business logic. */
class FilamentVisionApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: FilamentVisionDatabase by lazy { FilamentVisionDatabase.create(this) }
    val sessionRepository by lazy { RoomSessionRepository(database) }
    val measurementRepository by lazy { RoomMeasurementRepository(database.measurementDao()) }
    val alarmRepository by lazy { RoomAlarmRepository(database.alarmEventDao()) }
    val errorRepository by lazy { RoomErrorRepository(database) }
    val latestCameraFrameCache = LatestCameraFrameCache()
    val errorRecorder by lazy {
        PersistentErrorRecorder(errorRepository, latestCameraFrameCache, ErrorSnapshotFileStore(this), applicationScope)
    }
    val connectionSettingsRepository by lazy { SharedPreferencesConnectionSettingsRepository(this) }
    val visionPipelineRuntime: VisionPipelineRuntime by lazy {
        VisionPipelineRuntime(
            initialProfile = connectionSettingsRepository.saved.value.profile,
            sourceFactory = ImageInputSourceFactory()::create,
            dispatcher = Dispatchers.Default,
            scope = applicationScope,
        )
    }
    val monitoringRuntime: MonitoringRuntime by lazy {
        MonitoringRuntime(
            deviceConnection = UnavailableDeviceConnection(visionPipelineRuntime.inputState),
            visionSource = PipelineVisionSource(visionPipelineRuntime),
            sessionRepositoryProvider = { sessionRepository },
            measurementRepositoryProvider = { measurementRepository },
            alarmRepositoryProvider = { alarmRepository },
            errorRecorder = errorRecorder,
            dispatcher = Dispatchers.Default,
            initialAppliedProfileRevision = connectionSettingsRepository.saved.value.revision,
            configurationApplier = { visionPipelineRuntime.applyConnectionProfile(it) },
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(Dispatchers.IO) {
            errorRepository.reconcileSnapshotFiles(File(filesDir, "error_snapshots"))
        }
    }
}
