package com.filamentvision.data.settings

import com.filamentvision.model.BluetoothEndpointProfile
import com.filamentvision.model.BluetoothMode
import com.filamentvision.model.CalibrationProfile
import com.filamentvision.model.CameraProfile
import com.filamentvision.model.ConnectionProfile
import com.filamentvision.model.ConnectionTopology
import com.filamentvision.model.FrameBoundaryMode
import com.filamentvision.model.FrameParsingProfile
import com.filamentvision.model.FusionProfile
import com.filamentvision.model.ImageEncoding
import com.filamentvision.model.ImageFormatProfile
import com.filamentvision.model.PixelFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConnectionProfileCodecTest {
    @Test
    fun roundTripsIndependentCameraProfilesWithoutPersistingPassword() {
        val profile = ConnectionProfile(
            topology = ConnectionTopology.INDEPENDENT_CONNECTION,
            endpoints = listOf(
                BluetoothEndpointProfile(
                    "ble-a", BluetoothMode.BLE, "Camera A", "AA:BB:CC:DD:EE:FF", "uid-a",
                    "0000181a-0000-1000-8000-00805f9b34fb",
                    "00002a6e-0000-1000-8000-00805f9b34fb",
                    "00002a58-0000-1000-8000-00805f9b34fb", 247, 4,
                ),
            ),
            cameras = listOf(
                CameraProfile(
                    "A", "ble-a", "uid-a", "1", "main",
                    FrameParsingProfile(FrameBoundaryMode.LENGTH_FIELD, lengthFieldSizeBytes = 4, payloadOffset = 5),
                    ImageFormatProfile(
                        encoding = ImageEncoding.GRAYSCALE_RAW,
                        width = 640,
                        height = 480,
                        pixelFormat = PixelFormat.GRAY8,
                        rotationDegrees = 270,
                        mirrorHorizontally = true,
                        mirrorVertically = true,
                    ),
                    CalibrationProfile(0, 0, 640, 480, minimumPixelWidth = 20, maximumPixelWidth = 200, mmPerPixel = 0.01),
                ),
                CameraProfile("B", "ble-a"),
            ),
            preferredEndpointId = "ble-a",
            fusion = FusionProfile(0.4, 0.6, 0.75, 180),
        )

        val encoded = ConnectionProfileCodec.encode(profile)
        val decoded = ConnectionProfileCodec.decodeOrDefault(encoded)

        assertEquals(profile, decoded)
        assertFalse(encoded.contains("password", ignoreCase = true))
    }

    @Test
    fun malformedProfileFallsBackToUnconfigured() {
        assertEquals(ConnectionProfile(), ConnectionProfileCodec.decodeOrDefault("not-a-profile"))
    }
}
