package com.filamentvision.hardware

import com.filamentvision.model.ConnectionState
import kotlinx.coroutines.flow.StateFlow

/** Connection boundary that keeps Bluetooth and Wi-Fi APIs out of UI code. */
interface DeviceConnection : AutoCloseable {
    val state: StateFlow<ConnectionState>

    suspend fun connect()
    suspend fun reconnect()
    suspend fun disconnect()
}
