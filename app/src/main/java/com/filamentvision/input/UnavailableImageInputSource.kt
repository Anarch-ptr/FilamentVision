package com.filamentvision.input

import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.model.TransportProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

class UnavailableImageInputSource(
    initialState: InputState,
) : ImageInputSource {
    private val mutableState = MutableStateFlow(initialState)
    private val mutableDiagnostics = MutableStateFlow(InputDiagnostics())

    override val state: StateFlow<InputState> = mutableState
    override val frames: Flow<ArgbFrame> = emptyFlow()
    override val diagnostics: StateFlow<InputDiagnostics> = mutableDiagnostics
    override val activeProducerCount: Int get() = 0

    override suspend fun start() = Unit
    override suspend fun stop() = Unit
    override fun close() = Unit

    companion object {
        fun unconfigured() = UnavailableImageInputSource(InputState.Unconfigured)
        fun missingDrivers(protocols: Set<TransportProtocol>) =
            UnavailableImageInputSource(InputState.DriverUnavailable(protocols))
    }
}

