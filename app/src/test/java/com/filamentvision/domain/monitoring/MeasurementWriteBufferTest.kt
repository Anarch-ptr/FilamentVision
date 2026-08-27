package com.filamentvision.domain.monitoring

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementWriteBufferTest {
    @Test
    fun flushesOneBatchWhenConfiguredSizeIsReached() = runTest {
        val writes = mutableListOf<List<Int>>()
        val buffer = MeasurementWriteBuffer<Int>(
            scope = this,
            batchSize = 3,
            flushIntervalMillis = 1_000L,
            dispatcher = StandardTestDispatcher(testScheduler),
            writeBatch = { writes.add(it) },
        )

        buffer.add(1)
        buffer.add(2)
        assertEquals(emptyList<List<Int>>(), writes)
        buffer.add(3)

        assertEquals(listOf(listOf(1, 2, 3)), writes)
        assertEquals(0, buffer.pendingCount)
        buffer.close()
    }

    @Test
    fun periodicAndFinalFlushNeverLeaveAcceptedSamplesPending() = runTest {
        val writes = mutableListOf<List<Int>>()
        val buffer = MeasurementWriteBuffer<Int>(
            scope = this,
            batchSize = 8,
            flushIntervalMillis = 1_000L,
            dispatcher = StandardTestDispatcher(testScheduler),
            writeBatch = { writes.add(it) },
        )

        buffer.add(1)
        buffer.add(2)
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(listOf(listOf(1, 2)), writes)

        buffer.add(3)
        buffer.flush()
        assertEquals(listOf(listOf(1, 2), listOf(3)), writes)
        assertEquals(0, buffer.pendingCount)
        buffer.close()
    }
}
