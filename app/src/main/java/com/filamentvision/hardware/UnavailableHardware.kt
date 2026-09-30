package com.filamentvision.hardware

import com.filamentvision.input.InputState
import com.filamentvision.model.ConnectionState
import com.filamentvision.model.VisionMeasurement
import com.filamentvision.model.VisionSourceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

class UnavailableDeviceConnection(override val inputState: StateFlow<InputState>) : DeviceConnection {
    private val mutableConnectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> = mutableConnectionState
    override suspend fun connect() = Unit
    override suspend fun reconnect() = Unit
    override suspend fun disconnect() { mutableConnectionState.value = ConnectionState.DISCONNECTED }
    override fun close() { mutableConnectionState.value = ConnectionState.DISCONNECTED }
    constructor(initialInputState: InputState) : this(MutableStateFlow(initialInputState))
}

class UnavailableVisionSource(override val inputState: StateFlow<InputState>) : VisionSource {
    private val mutableSourceState = MutableStateFlow(VisionSourceState.STOPPED)
    private val mutableDiagnostics = MutableStateFlow(VisionSourceDiagnostics())
    override val state: StateFlow<VisionSourceState> = mutableSourceState
    override val measurements: Flow<VisionMeasurement> = emptyFlow()
    override val diagnostics: StateFlow<VisionSourceDiagnostics> = mutableDiagnostics
    override suspend fun start() = Unit
    override suspend fun stop() = Unit
    override fun close() = Unit
    constructor(initialInputState: InputState) : this(MutableStateFlow(initialInputState))
}
