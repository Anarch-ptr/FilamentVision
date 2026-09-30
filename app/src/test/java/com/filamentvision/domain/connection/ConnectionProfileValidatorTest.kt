package com.filamentvision.domain.connection

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
import com.filamentvision.model.TransportProtocol
import com.filamentvision.model.WifiEndpointProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionProfileValidatorTest {
    @Test
    fun sharedProfileRequiresBothCamerasToReferenceTheSameEndpoint() {
        val profile = validProfile().copy(
            topology = ConnectionTopology.SHARED_CONNECTION,
            cameras = listOf(
                validCamera("A", "wifi-main"),
                validCamera("B", "missing-endpoint"),
            ),
        )

        val result = ConnectionProfileValidator.validate(profile)

        assertTrue(result is ProfileValidation.Invalid)
        assertTrue((result as ProfileValidation.Invalid).errors.containsKey("cameras.B.endpointId"))
        assertTrue(result.errors.containsKey("topology"))
    }

    @Test
    fun rejectsInvalidPortImageAllocationCalibrationAndWeights() {
        val profile = validProfile().copy(
            endpoints = listOf(validWifi().copy(port = 70_000)),
            cameras = listOf(
                validCamera("A", "wifi-main").copy(
                    imageFormat = ImageFormatProfile(
                        encoding = ImageEncoding.RGB_RAW,
                        width = 100_000,
                        height = 100_000,
                        pixelFormat = PixelFormat.RGB24,
                    ),
                    calibration = validCalibration().copy(
                        minimumPixelWidth = 90,
                        maximumPixelWidth = 20,
                        mmPerPixel = 0.0,
                        minimumConfidence = 1.1,
                    ),
                ),
                validCamera("B", "wifi-main"),
            ),
            fusion = FusionProfile(cameraAWeight = -1.0, cameraBWeight = 0.0),
        )

        val result = ConnectionProfileValidator.validate(profile) as ProfileValidation.Invalid

        assertTrue(result.errors.containsKey("endpoints.wifi-main.port"))
        assertTrue(result.errors.containsKey("cameras.A.imageFormat.dimensions"))
        assertTrue(result.errors.containsKey("cameras.A.calibration.pixelWidthRange"))
        assertTrue(result.errors.containsKey("cameras.A.calibration.mmPerPixel"))
        assertTrue(result.errors.containsKey("cameras.A.calibration.minimumConfidence"))
        assertTrue(result.errors.containsKey("fusion.cameraAWeight"))
    }

    @Test
    fun validatesBluetoothUuidMtuAndHeaderLength() {
        val endpoint = BluetoothEndpointProfile(
            endpointId = "ble-main",
            mode = BluetoothMode.BLE,
            deviceName = "sensor",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceUid = "sensor-1",
            serviceUuid = "invalid",
            imageCharacteristicUuid = "also-invalid",
            commandCharacteristicUuid = "",
            mtu = 0,
            packetHeaderLength = -1,
        )
        val profile = validProfile().copy(
            endpoints = listOf(endpoint),
            cameras = listOf(validCamera("A", "ble-main"), validCamera("B", "ble-main")),
            preferredEndpointId = "ble-main",
        )

        val errors = (ConnectionProfileValidator.validate(profile) as ProfileValidation.Invalid).errors

        assertEquals(
            setOf(
                "endpoints.ble-main.serviceUuid",
                "endpoints.ble-main.imageCharacteristicUuid",
                "endpoints.ble-main.mtu",
                "endpoints.ble-main.packetHeaderLength",
            ),
            errors.keys,
        )
    }

    @Test
    fun acceptsCompleteSharedWifiProfile() {
        assertEquals(ProfileValidation.Valid, ConnectionProfileValidator.validate(validProfile()))
    }

    private fun validProfile() = ConnectionProfile(
        topology = ConnectionTopology.SHARED_CONNECTION,
        endpoints = listOf(validWifi()),
        cameras = listOf(validCamera("A", "wifi-main"), validCamera("B", "wifi-main")),
        preferredEndpointId = "wifi-main",
        autoReconnect = true,
        connectionTimeoutMillis = 5_000,
        fusion = FusionProfile(maximumTimestampDifferenceMillis = 250),
    )

    private fun validWifi() = WifiEndpointProfile(
        endpointId = "wifi-main",
        protocol = TransportProtocol.WIFI_TCP,
        host = "192.168.4.1",
        port = 5105,
        urlPath = "",
        username = "",
        credentialKey = null,
    )

    private fun validCamera(id: String, endpointId: String) = CameraProfile(
        cameraId = id,
        endpointId = endpointId,
        cameraUid = "camera-$id",
        channelId = id,
        streamId = id,
        frameParsing = FrameParsingProfile(
            boundaryMode = FrameBoundaryMode.LENGTH_FIELD,
            lengthFieldOffset = 0,
            lengthFieldSizeBytes = 4,
            maximumFrameBytes = 2_000_000,
        ),
        imageFormat = ImageFormatProfile(
            encoding = ImageEncoding.JPEG,
            width = 640,
            height = 480,
            pixelFormat = PixelFormat.ARGB8888,
        ),
        calibration = validCalibration(),
    )

    private fun validCalibration() = CalibrationProfile(
        roiLeft = 0,
        roiTop = 0,
        roiRight = 640,
        roiBottom = 480,
        minimumPixelWidth = 10,
        maximumPixelWidth = 200,
        mmPerPixel = 0.01,
        minimumConfidence = 0.7,
    )
}
