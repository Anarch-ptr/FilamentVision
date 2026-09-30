package com.filamentvision.hardware

import com.filamentvision.input.CreatedImageInput
import com.filamentvision.model.CalibrationProfile
import com.filamentvision.model.CameraProfile
import com.filamentvision.model.ConnectionProfile
import com.filamentvision.vision.runtime.ControlledImageInputSource
import com.filamentvision.vision.runtime.VisionPipelineRuntime
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineVisionSourceTest {
    @Test
    fun repeatedStartOwnsOneOfficialProducerAndStopReleasesIt() = runTest {
        val input = ControlledImageInputSource()
        val profile = ConnectionProfile(cameras = listOf(CameraProfile(
            cameraId = "A", endpointId = "source",
            calibration = CalibrationProfile(
                roiRight = 8, roiBottom = 4, manualThreshold = 40, edgeSensitivity = 20,
                minimumPixelWidth = 2, maximumPixelWidth = 6, mmPerPixel = 0.1, minimumConfidence = 0.5,
            ),
        )))
        val runtime = VisionPipelineRuntime(
            initialProfile = profile,
            sourceFactory = { CreatedImageInput(input, 1) },
            dispatcher = StandardTestDispatcher(testScheduler),
            scope = backgroundScope,
        )
        val source = PipelineVisionSource(runtime)

        source.start()
        source.start()
        runCurrent()

        assertEquals(1, source.activeProducerCount)
        assertEquals(1, runtime.diagnostics.value.officialMeasurementProducerCount)
        assertTrue(source.inputState.value is com.filamentvision.input.InputState.Streaming)

        source.stop()
        advanceUntilIdle()
        assertEquals(0, source.activeProducerCount)
        assertEquals(0, runtime.diagnostics.value.activeInputPipelineCount)
    }
}
