package com.filamentvision.domain.trend

import com.filamentvision.ui.components.chart.TrendChartPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendDownsamplerTest {
    @Test
    fun preservesChronologicalInputWhenItFitsPointBudget() {
        val points = (0L until 5L).map { index -> point(index, 1.75 + index / 1_000.0) }

        assertEquals(points, TrendDownsampler.downsample(points, maxPoints = 8))
    }

    @Test
    fun boundedOutputPreservesCriticalSpike() {
        val points = (0L until 1_000L).map { index ->
            point(index, if (index == 513L) 1.90 else 1.75)
        }

        val sampled = TrendDownsampler.downsample(points, maxPoints = 200)

        assertTrue(sampled.size <= 200)
        assertTrue(sampled.any { it.fusedDiameter == 1.90f })
        assertEquals(points.first(), sampled.first())
        assertEquals(points.last(), sampled.last())
        assertTrue(sampled.zipWithNext().all { (left, right) -> left.timestamp <= right.timestamp })
    }

    private fun point(timestamp: Long, diameter: Double) = TrendChartPoint(
        timestamp = timestamp,
        diameterA = diameter.toFloat(),
        diameterB = diameter.toFloat(),
        fusedDiameter = diameter.toFloat(),
    )
}
