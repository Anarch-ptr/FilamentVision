package com.filamentvision.input

import com.filamentvision.camera.model.ArgbFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ImageInputSource : AutoCloseable {
    val state: StateFlow<InputState>
    val frames: Flow<ArgbFrame>
    val diagnostics: StateFlow<InputDiagnostics>
    val activeProducerCount: Int

    suspend fun start()
    suspend fun stop()
}

