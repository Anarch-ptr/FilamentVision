package com.filamentvision.fake

import com.filamentvision.hardware.DeviceConnection
import com.filamentvision.input.InputState
import com.filamentvision.model.ConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Test-only connection state machine. Never packaged in release builds. */
class FakeDeviceConnection : DeviceConnection {
    private val operationMutex = Mutex()
    private val mutableState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val mutableInputState = MutableStateFlow<InputState>(InputState.WaitingForFrame)

    override val state: StateFlow<ConnectionState> = mutableState.asStateFlow()
    override val inputState: StateFlow<InputState> = mutableInputState.asStateFlow()

    override suspend fun connect() = operationMutex.withLock {
        if (mutableState.value == ConnectionState.CONNECTED) return@withLock

        transitionTo(ConnectionState.SEARCHING, SEARCH_DURATION_MILLIS)
        transitionTo(ConnectionState.CONNECTING, CONNECT_DURATION_MILLIS)
        mutableState.value = ConnectionState.CONNECTED
    }

    override suspend fun reconnect() = operationMutex.withLock {
        transitionTo(ConnectionState.RECONNECTING, RECONNECT_DURATION_MILLIS)
        mutableState.value = ConnectionState.CONNECTED
    }

    override suspend fun disconnect() = operationMutex.withLock {
        mutableState.value = ConnectionState.DISCONNECTED
    }

    fun simulateConnectionError() {
        mutableState.value = ConnectionState.ERROR
    }

    override fun close() {
        mutableState.value = ConnectionState.DISCONNECTED
    }

    private suspend fun transitionTo(
        nextState: ConnectionState,
        durationMillis: Long,
    ) {
        mutableState.value = nextState
        delay(durationMillis)
    }

    private companion object {
        const val SEARCH_DURATION_MILLIS = 500L
        const val CONNECT_DURATION_MILLIS = 650L
        const val RECONNECT_DURATION_MILLIS = 700L
    }
}
