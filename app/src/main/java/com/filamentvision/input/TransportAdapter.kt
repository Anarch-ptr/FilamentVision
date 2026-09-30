package com.filamentvision.input

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Raw bytes delivered by a concrete Wi-Fi or Bluetooth driver. */
data class TransportChunk(
    val endpointId: String,
    val receivedAtMillis: Long,
    val bytes: ByteArray,
)

/**
 * Hardware transport boundary. A future protocol module implements this interface; the core
 * runtime never assumes a socket, BLE characteristic, camera SDK, or packet layout.
 */
interface TransportAdapter : AutoCloseable {
    val state: StateFlow<InputState>
    val chunks: Flow<TransportChunk>
    val diagnostics: StateFlow<InputDiagnostics>

    suspend fun connect()
    suspend fun disconnect()
}

