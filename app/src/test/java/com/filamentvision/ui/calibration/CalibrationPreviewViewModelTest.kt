package com.filamentvision.ui.calibration

import com.filamentvision.data.settings.ConnectionSettingsRepository
import com.filamentvision.data.settings.SavedConnectionProfile
import com.filamentvision.domain.connection.ProfileValidation
import com.filamentvision.input.CreatedImageInput
import com.filamentvision.model.CalibrationProfile
import com.filamentvision.model.CameraProfile
import com.filamentvision.model.ConnectionProfile
import com.filamentvision.vision.runtime.ControlledImageInputSource
import com.filamentvision.vision.runtime.VisionPipelineRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationPreviewViewModelTest {
    @Test
    fun editsStayDraftOnlyAndResetRestoresOpeningSnapshot() = runTest {
        val source = ControlledImageInputSource()
        val saved = profile(threshold = 100)
        val repository = InMemorySettingsRepository(saved)
        val runtime = VisionPipelineRuntime(
            initialProfile = saved,
            sourceFactory = { CreatedImageInput(source, 1) },
            dispatcher = StandardTestDispatcher(testScheduler),
            scope = backgroundScope,
        )
        val viewModel = CalibrationPreviewViewModel(
            cameraId = "A",
            repository = repository,
            runtime = runtime,
            scope = backgroundScope,
        )
        runCurrent()

        viewModel.updateManualThreshold(140)

        assertEquals(140, viewModel.state.value.draft.current.calibration.manualThreshold)
        assertEquals(100, repository.profile.value.cameras.single().calibration.manualThreshold)
        assertEquals(100, runtime.profile.value.cameras.single().calibration.manualThreshold)
        assertTrue(viewModel.state.value.hasUnsavedChanges)

        viewModel.reset()
        assertEquals(100, viewModel.state.value.draft.current.calibration.manualThreshold)
        assertFalse(viewModel.state.value.hasUnsavedChanges)

        viewModel.close()
        advanceUntilIdle()
    }

    @Test
    fun discardAndRestoreDefaultsDoNotPersist() = runTest {
        val source = ControlledImageInputSource()
        val repository = InMemorySettingsRepository(profile(100))
        val runtime = VisionPipelineRuntime(
            initialProfile = repository.profile.value,
            sourceFactory = { CreatedImageInput(source, 1) },
            dispatcher = StandardTestDispatcher(testScheduler), scope = backgroundScope,
        )
        val viewModel = CalibrationPreviewViewModel("A", repository, runtime, backgroundScope)
        runCurrent()

        viewModel.restoreDefaults()
        assertEquals(CalibrationProfile(), viewModel.state.value.draft.current.calibration)
        assertTrue(viewModel.state.value.hasUnsavedChanges)
        viewModel.discard()
        assertEquals(100, viewModel.state.value.draft.current.calibration.manualThreshold)
        assertEquals(0, repository.saveCount)

        viewModel.close()
        advanceUntilIdle()
    }

    @Test
    fun savePersistsRevisionWhileApplyChangesTheRuntimeSnapshot() = runTest {
        val source = ControlledImageInputSource()
        val repository = InMemorySettingsRepository(profile(100))
        val runtime = VisionPipelineRuntime(
            initialProfile = repository.profile.value,
            sourceFactory = { CreatedImageInput(source, 1) },
            dispatcher = StandardTestDispatcher(testScheduler), scope = backgroundScope,
        )
        val viewModel = CalibrationPreviewViewModel("A", repository, runtime, backgroundScope)
        runCurrent()

        viewModel.updateManualThreshold(140)
        viewModel.save()
        runCurrent()
        assertEquals(1L, repository.saved.value.revision)
        assertEquals(100, runtime.profile.value.cameras.single().calibration.manualThreshold)
        assertEquals(CalibrationPersistenceStatus.SAVED_APPLY_REQUIRED, viewModel.state.value.persistenceStatus)

        viewModel.apply()
        runCurrent()
        assertEquals(140, runtime.profile.value.cameras.single().calibration.manualThreshold)
        assertEquals(CalibrationPersistenceStatus.APPLIED, viewModel.state.value.persistenceStatus)
        viewModel.close()
    }

    private fun profile(threshold: Int) = ConnectionProfile(cameras = listOf(
        CameraProfile("A", "source", calibration = CalibrationProfile(
            roiRight = 8, roiBottom = 4, manualThreshold = threshold, edgeSensitivity = 20,
            minimumPixelWidth = 2, maximumPixelWidth = 6, mmPerPixel = 0.1, minimumConfidence = 0.5,
        )),
    ))
}

private class InMemorySettingsRepository(initial: ConnectionProfile) : ConnectionSettingsRepository {
    private val mutableProfile = MutableStateFlow(initial)
    override val profile: StateFlow<ConnectionProfile> = mutableProfile
    private val mutableSaved = MutableStateFlow(SavedConnectionProfile(initial, 0L))
    override val saved: StateFlow<SavedConnectionProfile> = mutableSaved
    var saveCount = 0
    override fun save(profile: ConnectionProfile): ProfileValidation {
        saveCount++
        mutableProfile.value = profile
        mutableSaved.value = SavedConnectionProfile(profile, mutableSaved.value.revision + 1)
        return ProfileValidation.Valid
    }
}
