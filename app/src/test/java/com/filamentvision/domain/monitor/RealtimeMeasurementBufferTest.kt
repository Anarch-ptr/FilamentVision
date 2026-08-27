package com.filamentvision.domain.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeMeasurementBufferTest {
    @Test
    fun replacesOldestValuesWithoutGrowingPastCapacity() {
        val buffer = RealtimeMeasurementBuffer<Int>(capacity = 3)

        (1..10).forEach(buffer::add)

        assertEquals(3, buffer.size)
        assertEquals(listOf(8, 9, 10), buffer.snapshot())
    }

    @Test
    fun clearReleasesAllBufferedValues() {
        val buffer = RealtimeMeasurementBuffer<Int>(capacity = 3)
        buffer.add(1)
        buffer.add(2)

        buffer.clear()

        assertEquals(0, buffer.size)
        assertEquals(emptyList<Int>(), buffer.snapshot())
    }
}
