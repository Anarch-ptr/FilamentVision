package com.filamentvision.fake

import com.filamentvision.model.ConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeDeviceConnectionTest {
    @Test
    fun connectReconnectAndDisconnectFollowProductStates() = runTest {
        val connection = FakeDeviceConnection()

        val connect = async { connection.connect() }
        runCurrent()
        assertEquals(ConnectionState.SEARCHING, connection.state.value)
        advanceUntilIdle()
        connect.await()
        assertEquals(ConnectionState.CONNECTED, connection.state.value)

        val reconnect = async { connection.reconnect() }
        runCurrent()
        assertEquals(ConnectionState.RECONNECTING, connection.state.value)
        advanceUntilIdle()
        reconnect.await()
        assertEquals(ConnectionState.CONNECTED, connection.state.value)

        connection.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, connection.state.value)
    }
}
