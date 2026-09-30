package com.filamentvision.input

import com.filamentvision.model.CameraProfile
import com.filamentvision.model.ConnectionProfile
import com.filamentvision.model.ConnectionTopology
import com.filamentvision.model.TransportProtocol
import com.filamentvision.model.WifiEndpointProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageInputSourceFactoryTest {
    @Test
    fun unconfiguredProfileCreatesZeroProducerSource() = runTest {
        val created = ImageInputSourceFactory().create(ConnectionProfile())

        created.source.start()

        assertEquals(0, created.endpointRuntimeCount)
        assertEquals(0, created.source.activeProducerCount)
        assertEquals(InputState.Unconfigured, created.source.state.value)
    }

    @Test
    fun sharedEndpointIsCreatedOnceAndReportsDriverUnavailable() = runTest {
        val endpoint = WifiEndpointProfile("shared", TransportProtocol.WIFI_TCP, "camera.local", 5105)
        val profile = ConnectionProfile(
            topology = ConnectionTopology.SHARED_CONNECTION,
            endpoints = listOf(endpoint),
            cameras = listOf(CameraProfile("A", "shared"), CameraProfile("B", "shared")),
            preferredEndpointId = "shared",
        )

        val created = ImageInputSourceFactory().create(profile)
        created.source.start()

        assertEquals(1, created.endpointRuntimeCount)
        assertTrue(created.source.state.value is InputState.DriverUnavailable)
        assertEquals(0, created.source.activeProducerCount)
    }
}

