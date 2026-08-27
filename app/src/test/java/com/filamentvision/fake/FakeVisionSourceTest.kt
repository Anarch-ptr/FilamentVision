package com.filamentvision.fake

import com.filamentvision.model.VisionSourceState
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeVisionSourceTest {
    @Test
    fun emittedMeasurementRetainsBothCameraValuesAndConsistentDifference() = runTest {
        val source = FakeVisionSource(
            samplePeriodMillis = 1_000L,
            dispatcher = StandardTestDispatcher(testScheduler),
            nowMillis = { 123L },
        )

        source.start()
        runCurrent()
        val measurement = source.measurements.first()

        assertEquals(VisionSourceState.LIVE, source.state.value)
        assertEquals(123L, measurement.timestamp)
        assertEquals(
            abs(measurement.diameterA - measurement.diameterB),
            measurement.shapeDifference,
            0.000_001,
        )
        assertTrue(measurement.fusedDiameter in 1.70..1.80)

        source.close()
    }

    @Test
    fun concurrentStartCallsCreateExactlyOneProducer() = runTest {
        val source = FakeVisionSource(
            samplePeriodMillis = 125L,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        List(20) { async { source.start() } }.awaitAll()
        runCurrent()

        assertEquals(1, source.activeProducerCount)
        assertEquals(1, source.totalProducerStarts)

        source.stop()
        assertEquals(0, source.activeProducerCount)
        assertEquals(VisionSourceState.STOPPED, source.state.value)
        source.close()
    }

    @Test
    fun producerEmitsWithinFiveToTenHertzAndStopsCleanly() = runTest {
        val source = FakeVisionSource(
            samplePeriodMillis = 125L,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        source.start()
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()
        val samplesAfterOneSecond = source.emittedSampleCount

        assertTrue(samplesAfterOneSecond in 5L..10L)
        source.stop()
        val samplesAtStop = source.emittedSampleCount
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(samplesAtStop, source.emittedSampleCount)
        assertEquals(0, source.activeProducerCount)
        source.close()
    }
}
