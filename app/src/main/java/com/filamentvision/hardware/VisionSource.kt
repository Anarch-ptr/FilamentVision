package com.filamentvision.hardware

import com.filamentvision.model.VisionMeasurement
import com.filamentvision.model.VisionSourceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Hardware-neutral stream of compact measurements; preview frames use a separate future path. */
interface VisionSource : AutoCloseable {
    val state: StateFlow<VisionSourceState>
    val measurements: Flow<VisionMeasurement>

    suspend fun start()
    suspend fun stop()
}

