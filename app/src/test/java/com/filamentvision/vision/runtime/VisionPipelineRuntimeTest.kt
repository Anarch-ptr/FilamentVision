package com.filamentvision.vision.runtime

import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.input.CreatedImageInput
import com.filamentvision.input.ImageInputSource
import com.filamentvision.input.InputDiagnostics
import com.filamentvision.input.InputState
import com.filamentvision.model.CalibrationProfile
import com.filamentvision.model.CameraProfile
import com.filamentvision.model.ConnectionProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VisionPipelineRuntimeTest {
    @Test
    fun twoPreviewObserversReuseOneInputAndRetainOnlyLatestResult() = runTest {
        val source = ControlledImageInputSource()
        val runtime = runtime(source, StandardTestDispatcher(testScheduler), backgroundScope)

        val first = runtime.acquirePreview("diagnostics")
        val second = runtime.acquirePreview("calibration-A")
        runCurrent()
        source.emit(frame(sequence = 1))
        source.emit(frame(sequence = 2))
        runCurrent()
        advanceTimeBy(125L)
        runCurrent()

        assertEquals(1, runtime.diagnostics.value.activeInputPipelineCount)
        assertEquals(2, runtime.diagnostics.value.previewObserverCount)
        assertEquals(2L, runtime.results("A").value!!.sequence)

        first.release()
        second.release()
        advanceUntilIdle()
        assertEquals(0, runtime.diagnostics.value.activeInputPipelineCount)
        assertEquals(1, source.startCount)
        assertEquals(1, source.stopCount)
    }

    @Test
    fun draftPreviewUsesLatestFrameWithoutPublishingOfficialMeasurement() = runTest {
        val source = ControlledImageInputSource()
        val runtime = runtime(source, StandardTestDispatcher(testScheduler), backgroundScope)
        val lease = runtime.acquirePreview("calibration-A")
        val draft = MutableStateFlow(profile().cameras.single())
        val draftResult = runtime.draftPreview("A", draft)
        val collection = backgroundScope.launchCollect(draftResult)
        runCurrent()

        source.emit(frame(sequence = 4))
        runCurrent()
        draft.value = draft.value.copy(calibration = draft.value.calibration.copy(manualThreshold = 30))
        runCurrent()

        assertNotNull(draftResult.value)
        assertEquals(30, draftResult.value!!.effectiveThreshold)
        assertEquals(0, runtime.diagnostics.value.officialMeasurementProducerCount)

        collection.cancel()
        lease.release()
        advanceUntilIdle()
    }

    private fun runtime(
        source: ControlledImageInputSource,
        dispatcher: CoroutineDispatcher,
        scope: CoroutineScope,
    ) = VisionPipelineRuntime(
        initialProfile = profile(),
        sourceFactory = { CreatedImageInput(source, 1) },
        dispatcher = dispatcher,
        scope = scope,
    )

    private fun profile() = ConnectionProfile(
        cameras = listOf(
            CameraProfile(
                cameraId = "A",
                endpointId = "source",
                calibration = CalibrationProfile(
                    roiLeft = 0, roiTop = 0, roiRight = 8, roiBottom = 4,
                    manualThreshold = 40, edgeSensitivity = 20,
                    minimumPixelWidth = 2, maximumPixelWidth = 6,
                    mmPerPixel = 0.1, minimumConfidence = 0.5,
                ),
            ),
        ),
    )

    private fun frame(sequence: Long) = ArgbFrame(
        sourceId = "A", timestampMillis = sequence, width = 8, height = 4,
        pixels = IntArray(32) { if (it % 8 in 2 until 6) 0xFF101010.toInt() else 0xFFFFFFFF.toInt() },
        sequence = sequence,
    )
}

private fun <T> kotlinx.coroutines.CoroutineScope.launchCollect(flow: Flow<T>) =
    launch { flow.collect {} }

internal class ControlledImageInputSource : ImageInputSource {
    private val mutableState = MutableStateFlow<InputState>(InputState.Stopped)
    private val mutableDiagnostics = MutableStateFlow(InputDiagnostics())
    private val mutableFrames = MutableSharedFlow<ArgbFrame>(extraBufferCapacity = 8)
    override val state: StateFlow<InputState> = mutableState
    override val diagnostics: StateFlow<InputDiagnostics> = mutableDiagnostics
    override val frames: Flow<ArgbFrame> = mutableFrames
    override val activeProducerCount: Int get() = mutableDiagnostics.value.activeProducerCount
    var startCount = 0
    var stopCount = 0

    override suspend fun start() {
        if (activeProducerCount == 1) return
        startCount++
        mutableState.value = InputState.Streaming
        mutableDiagnostics.value = mutableDiagnostics.value.copy(activeProducerCount = 1)
    }

    override suspend fun stop() {
        if (activeProducerCount == 0) return
        stopCount++
        mutableState.value = InputState.Stopped
        mutableDiagnostics.value = mutableDiagnostics.value.copy(activeProducerCount = 0)
    }

    suspend fun emit(frame: ArgbFrame) = mutableFrames.emit(frame)
    override fun close() = Unit
}
