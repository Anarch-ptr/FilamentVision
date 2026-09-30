package com.filamentvision.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Serializes and validates state updates made by a future transport/image-input implementation. */
class InputStateController(initialState: InputState = InputState.Unconfigured) {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<InputState> = mutableState.asStateFlow()

    @Synchronized
    fun transitionTo(next: InputState): Boolean {
        if (next == mutableState.value) return true
        if (!isAllowed(mutableState.value, next)) return false
        mutableState.value = next
        return true
    }

    private fun isAllowed(current: InputState, next: InputState): Boolean = when (current) {
        InputState.Unconfigured -> next is InputState.DriverUnavailable || next == InputState.Connecting || next == InputState.Stopped
        is InputState.DriverUnavailable -> next == InputState.Connecting || next == InputState.Unconfigured || next == InputState.Stopped
        InputState.Connecting -> next == InputState.Connected || next == InputState.WaitingForFrame ||
            next is InputState.ConnectionError || next is InputState.DriverUnavailable || next == InputState.Stopped
        InputState.Connected -> next == InputState.WaitingForFrame || next == InputState.Streaming ||
            next == InputState.Reconnecting || next is InputState.ConnectionError || next == InputState.Stopped
        InputState.WaitingForFrame -> next == InputState.Streaming || next == InputState.Reconnecting ||
            next is InputState.DataFormatError || next is InputState.ConnectionError || next == InputState.Stopped
        InputState.Streaming -> next == InputState.WaitingForFrame || next == InputState.Reconnecting ||
            next is InputState.DataFormatError || next is InputState.ConnectionError || next == InputState.Stopped
        InputState.Reconnecting -> next == InputState.Connected || next == InputState.WaitingForFrame ||
            next is InputState.ConnectionError || next is InputState.DriverUnavailable || next == InputState.Stopped
        is InputState.DataFormatError -> next == InputState.WaitingForFrame || next == InputState.Streaming ||
            next == InputState.Reconnecting || next == InputState.Stopped
        is InputState.ConnectionError -> next == InputState.Reconnecting || next == InputState.Connecting || next == InputState.Stopped
        InputState.Stopped -> next == InputState.Connecting || next == InputState.Unconfigured || next is InputState.DriverUnavailable
    }
}

