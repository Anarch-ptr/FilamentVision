package com.filamentvision.camera.source

import com.filamentvision.camera.media.FakeCameraMediaProvider
import com.filamentvision.camera.model.CameraVisualSourceState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraVisualSourceTest {
    @Test
    fun staticSourceEmitsOneFrameAndRepeatedStartDoesNotDuplicateProducer() = runTest {
        val source = FakeImageVisualSource(
            cameraId = "A",
            mediaProvider = FakeCameraMediaProvider(width = 16, height = 12),
            scope = backgroundScope,
        )
        val frame = async { source.frames.first() }

        source.start()
        source.start()
        runCurrent()

        assertEquals(1, source.activeProducerCount)
        assertEquals(16, frame.await().width)
        assertEquals(CameraVisualSourceState.LIVE, source.state.value)

        source.stop()
        assertEquals(0, source.activeProducerCount)
        assertEquals(CameraVisualSourceState.STOPPED, source.state.value)
    }

    @Test
    fun frameSequenceStopsEmittingAndRapidLifecycleRemainsIdempotent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mediaProvider = FakeCameraMediaProvider(width = 16, height = 12)
        val source = FakeFrameSequenceVisualSource(
            cameraId = "B",
            mediaProvider = mediaProvider,
            scope = backgroundScope,
            dispatcher = dispatcher,
            frameIntervalMillis = 100L,
        )
        val sequences = mutableListOf<Long>()
        val collector = backgroundScope.async(dispatcher) {
            repeat(3) { sequences += source.frames.first { it.sequence > sequences.lastOrNull() ?: -1L }.sequence }
        }

        repeat(5) {
            source.start()
            source.start()
            runCurrent()
            assertEquals(1, source.activeProducerCount)
            source.stop()
            assertEquals(0, source.activeProducerCount)
        }

        source.start()
        advanceTimeBy(350L)
        runCurrent()
        source.stop()
        val countAfterStop = source.emittedFrameCount
        advanceTimeBy(500L)
        advanceUntilIdle()

        assertTrue(sequences.isNotEmpty())
        assertEquals(countAfterStop, source.emittedFrameCount)
        assertTrue(mediaProvider.generatedPixelBufferCount <= 8)
        assertEquals(0, source.activeProducerCount)
        collector.cancel()
    }

    @Test
    fun cancellingOwnerScopeClearsLocalAndGlobalVisualProducerCounts() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ownerJob = SupervisorJob()
        val source = FakeFrameSequenceVisualSource(
            cameraId = "A",
            mediaProvider = FakeCameraMediaProvider(width = 16, height = 12),
            scope = CoroutineScope(ownerJob + dispatcher),
            dispatcher = dispatcher,
        )

        source.start()
        runCurrent()
        assertEquals(1, source.activeProducerCount)
        assertEquals(1, CameraVisualDiagnostics.activeProducerCount)

        ownerJob.cancel()
        runCurrent()

        assertEquals(0, source.activeProducerCount)
        assertEquals(0, CameraVisualDiagnostics.activeProducerCount)
    }
}
