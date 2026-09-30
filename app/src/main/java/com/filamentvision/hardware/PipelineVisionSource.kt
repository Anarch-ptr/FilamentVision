package com.filamentvision.hardware

import com.filamentvision.input.InputState
import com.filamentvision.model.VisionMeasurement
import com.filamentvision.model.VisionSourceState
import com.filamentvision.vision.runtime.VisionPipelineRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class PipelineVisionSource(
    private val runtime: VisionPipelineRuntime,
) : VisionSource {
    override val state: StateFlow<VisionSourceState> = runtime.visionState
    override val inputState: StateFlow<InputState> = runtime.inputState
    override val measurements: Flow<VisionMeasurement> = runtime.officialMeasurements
    override val diagnostics: StateFlow<VisionSourceDiagnostics> = runtime.sourceDiagnostics

    override suspend fun start() = runtime.startOfficialMeasurements()
    override suspend fun stop() = runtime.stopOfficialMeasurements()
    override fun close() = runtime.close()
}
