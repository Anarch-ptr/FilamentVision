package com.filamentvision.hardware

import com.filamentvision.input.InputState
import com.filamentvision.model.VisionMeasurement
import com.filamentvision.model.VisionSourceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Hardware-neutral stream of compact measurements; preview frames use a separate future path. */
interface VisionSource : AutoCloseable {
    val state: StateFlow<VisionSourceState>
    val inputState: StateFlow<InputState>
    val measurements: Flow<VisionMeasurement>
    val diagnostics: StateFlow<VisionSourceDiagnostics>

    val activeProducerCount: Int get() = diagnostics.value.activeProducerCount
    val totalProducerStarts: Int get() = diagnostics.value.totalProducerStarts
    val emittedSampleCount: Long get() = diagnostics.value.emittedMeasurementCount

    suspend fun start()
    suspend fun stop()
}

data class VisionSourceDiagnostics(
    val activeProducerCount: Int = 0,
    val totalProducerStarts: Int = 0,
    val emittedMeasurementCount: Long = 0,
)
