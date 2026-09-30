package com.filamentvision.camera.source

import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.camera.model.CameraVisualSourceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface CameraVisualSource {
    val state: StateFlow<CameraVisualSourceState>
    val frames: Flow<ArgbFrame>
    val activeProducerCount: Int
    val emittedFrameCount: Long

    suspend fun start()
    suspend fun stop()
}
