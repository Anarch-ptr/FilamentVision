package com.filamentvision.domain.alarm

import com.filamentvision.model.MeasurementStatus
import com.filamentvision.model.VisionMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmEvaluatorTest {
    @Test
    fun evaluatesDynamicTwoAndFivePercentBoundaries() {
        val evaluator = AlarmEvaluator(targetDiameter = 2.0)

        assertEquals(AlarmLevel.NORMAL, evaluator.evaluate(measurement(2.039)).state.level)
        assertEquals(AlarmLevel.WARNING, evaluator.evaluate(measurement(2.041)).state.level)
        assertEquals(AlarmDirection.HIGH, evaluator.currentState.direction)
        assertEquals(AlarmLevel.CRITICAL, evaluator.evaluate(measurement(2.101)).state.level)
        assertEquals(AlarmLevel.WARNING, evaluator.evaluate(measurement(1.959)).state.level)
        assertEquals(AlarmDirection.LOW, evaluator.currentState.direction)
        assertEquals(AlarmLevel.CRITICAL, evaluator.evaluate(measurement(1.899)).state.level)
    }

    @Test
    fun emitsOnlyTransitionsAndUsesMeasurementTimestamp() {
        val evaluator = AlarmEvaluator(targetDiameter = 1.75)

        assertNull(evaluator.evaluate(measurement(1.75, timestamp = 100L)).transition)
        val warning = evaluator.evaluate(measurement(1.79, timestamp = 125L)).transition
        assertEquals(AlarmLevel.NORMAL, warning?.from?.level)
        assertEquals(AlarmLevel.WARNING, warning?.to?.level)
        assertEquals(125L, warning?.timestamp)
        assertNull(evaluator.evaluate(measurement(1.80, timestamp = 250L)).transition)
        val normal = evaluator.evaluate(measurement(1.75, timestamp = 375L)).transition
        assertEquals(AlarmLevel.WARNING, normal?.from?.level)
        assertEquals(AlarmLevel.NORMAL, normal?.to?.level)
        assertEquals(375L, normal?.timestamp)
    }

    private fun measurement(diameter: Double, timestamp: Long = 0L) = VisionMeasurement(
        diameterA = diameter,
        diameterB = diameter,
        fusedDiameter = diameter,
        shapeDifference = 0.0,
        cameraAConfidence = 0.98,
        cameraBConfidence = 0.97,
        confidence = 0.975,
        status = MeasurementStatus.NORMAL,
        timestamp = timestamp,
    )
}
