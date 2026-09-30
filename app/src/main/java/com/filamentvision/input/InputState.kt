package com.filamentvision.input

import com.filamentvision.model.TransportProtocol

sealed interface InputState {
    data object Unconfigured : InputState
    data class DriverUnavailable(val protocols: Set<TransportProtocol>) : InputState
    data object Connecting : InputState
    data object Connected : InputState
    data object WaitingForFrame : InputState
    data object Streaming : InputState
    data object Reconnecting : InputState
    data class DataFormatError(val message: String) : InputState
    data class ConnectionError(val message: String) : InputState
    data object Stopped : InputState
}

