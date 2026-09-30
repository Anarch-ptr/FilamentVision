package com.filamentvision.ui.calibration

import com.filamentvision.data.settings.ConnectionSettingsRepository
import com.filamentvision.domain.connection.ConnectionProfileValidator
import com.filamentvision.domain.connection.ProfileValidation
import com.filamentvision.model.CalibrationProfile
import com.filamentvision.model.CameraProfile
import com.filamentvision.model.ImageFormatProfile
import com.filamentvision.vision.processing.VisionProcessingResult
import com.filamentvision.vision.runtime.PreviewLease
import com.filamentvision.vision.runtime.VisionPipelineRuntime
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalibrationPreviewUiState(
    val draft: CalibrationDraft,
    val result: VisionProcessingResult? = null,
    val validationErrors: Map<String, String> = emptyMap(),
    val persistenceStatus: CalibrationPersistenceStatus = CalibrationPersistenceStatus.UNCHANGED,
) {
    val hasUnsavedChanges: Boolean get() = draft.hasUnsavedChanges
}

enum class CalibrationPersistenceStatus { UNCHANGED, UNSAVED, SAVED_APPLY_REQUIRED, APPLIED, INVALID }

class CalibrationPreviewViewModel(
    private val cameraId: String,
    private val repository: ConnectionSettingsRepository,
    private val runtime: VisionPipelineRuntime,
    private val scope: CoroutineScope,
    private val applySavedProfile: suspend (com.filamentvision.data.settings.SavedConnectionProfile) -> Unit = {
        runtime.applyConnectionProfile(it)
    },
) : Closeable {
    private val openingProfile = repository.profile.value
    private val openingCamera = openingProfile.cameras.firstOrNull { it.cameraId.equals(cameraId, true) }
        ?: CameraProfile(cameraId.uppercase(), "")
    private val mutableDraftProfile = MutableStateFlow(openingCamera)
    private val mutableState = MutableStateFlow(CalibrationPreviewUiState(CalibrationDraft(openingCamera, openingCamera)))
    val state: StateFlow<CalibrationPreviewUiState> = mutableState.asStateFlow()
    private var lease: PreviewLease? = null
    private val previewJob: Job

    init {
        val preview = runtime.draftPreview(cameraId, mutableDraftProfile)
        previewJob = scope.launch {
            lease = runtime.acquirePreview("calibration-${cameraId.uppercase()}-${hashCode()}")
            preview.collect { result -> mutableState.update { it.copy(result = result) } }
        }
    }

    fun updateManualThreshold(value: Int) = updateCalibration { it.copy(manualThreshold = value) }
    fun updateMmPerPixel(value: Double) = updateCalibration { it.copy(mmPerPixel = value) }
    fun updateCalibration(transform: (CalibrationProfile) -> CalibrationProfile) =
        setCurrent(mutableDraftProfile.value.copy(calibration = transform(mutableDraftProfile.value.calibration)))

    fun updateGeometry(transform: (ImageFormatProfile) -> ImageFormatProfile) =
        setCurrent(mutableDraftProfile.value.copy(imageFormat = transform(mutableDraftProfile.value.imageFormat)))

    fun reset() = setCurrent(openingCamera)
    fun discard() = reset()
    fun restoreDefaults() = setCurrent(mutableDraftProfile.value.copy(calibration = CalibrationProfile()))

    fun save() {
        val current = mutableDraftProfile.value
        val fullProfile = profileWith(current)
        when (repository.save(fullProfile)) {
            ProfileValidation.Valid -> {
                val savedCamera = repository.profile.value.cameras.first { it.cameraId.equals(cameraId, true) }
                mutableState.update {
                    it.copy(
                        draft = CalibrationDraft(savedCamera, current),
                        persistenceStatus = CalibrationPersistenceStatus.SAVED_APPLY_REQUIRED,
                        validationErrors = emptyMap(),
                    )
                }
            }
            is ProfileValidation.Invalid -> mutableState.update {
                it.copy(persistenceStatus = CalibrationPersistenceStatus.INVALID)
            }
        }
    }

    fun apply() {
        if (mutableState.value.hasUnsavedChanges) save()
        if (mutableState.value.persistenceStatus == CalibrationPersistenceStatus.INVALID) return
        val saved = repository.saved.value
        scope.launch {
            applySavedProfile(saved)
            mutableState.update { it.copy(persistenceStatus = CalibrationPersistenceStatus.APPLIED) }
        }
    }

    override fun close() {
        previewJob.cancel()
        scope.launch { lease?.release() }
    }

    private fun setCurrent(camera: CameraProfile) {
        mutableDraftProfile.value = camera
        val validation = ConnectionProfileValidator.validate(profileWith(camera))
        val errors = (validation as? ProfileValidation.Invalid)?.errors.orEmpty()
        mutableState.update {
            it.copy(
                draft = CalibrationDraft(it.draft.saved, camera),
                validationErrors = errors,
                persistenceStatus = CalibrationPersistenceStatus.UNSAVED,
            )
        }
    }

    private fun profileWith(camera: CameraProfile) = repository.profile.value.copy(
        cameras = repository.profile.value.cameras.map { if (it.cameraId.equals(cameraId, true)) camera else it },
    )
}
