package com.filamentvision.domain.connection

import com.filamentvision.model.BluetoothConnectionConfig
import com.filamentvision.model.WifiConnectionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionConfigValidatorTest {
    @Test
    fun validWifiNormalizesTextAndParsesPort() {
        val result = ConnectionConfigValidator.validateWifi(
            deviceUid = "  FV-001  ",
            host = " 192.168.4.1 ",
            port = "8080",
        )

        assertEquals(
            ConnectionValidation.Valid(WifiConnectionConfig("FV-001", "192.168.4.1", 8080)),
            result,
        )
    }

    @Test
    fun wifiRejectsMissingUidHostAndOutOfRangePort() {
        val result = ConnectionConfigValidator.validateWifi(" ", "", "70000")

        assertTrue(result is ConnectionValidation.Invalid)
        assertEquals(
            setOf(ConnectionField.DEVICE_UID, ConnectionField.HOST, ConnectionField.PORT),
            (result as ConnectionValidation.Invalid).errors.keys,
        )
    }

    @Test
    fun bluetoothAcceptsCanonicalUuidsAndNormalizesMacAddress() {
        val result = ConnectionConfigValidator.validateBluetooth(
            deviceUid = "sensor-a",
            deviceAddress = "aa-bb-cc-dd-ee-ff",
            serviceUuid = "0000181a-0000-1000-8000-00805f9b34fb",
            measurementUuid = "00002a6e-0000-1000-8000-00805f9b34fb",
            commandUuid = "00002a58-0000-1000-8000-00805f9b34fb",
        )

        assertEquals(
            ConnectionValidation.Valid(
                BluetoothConnectionConfig(
                    deviceUid = "sensor-a",
                    deviceAddress = "AA:BB:CC:DD:EE:FF",
                    serviceUuid = "0000181a-0000-1000-8000-00805f9b34fb",
                    measurementCharacteristicUuid = "00002a6e-0000-1000-8000-00805f9b34fb",
                    commandCharacteristicUuid = "00002a58-0000-1000-8000-00805f9b34fb",
                ),
            ),
            result,
        )
    }

    @Test
    fun bluetoothRejectsAbbreviatedUuidGroups() {
        val result = ConnectionConfigValidator.validateBluetooth(
            deviceUid = "sensor-a",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            serviceUuid = "1-1-1-1-1",
            measurementUuid = "00002a6e-0000-1000-8000-00805f9b34fb",
            commandUuid = "00002a58-0000-1000-8000-00805f9b34fb",
        )

        assertTrue(result is ConnectionValidation.Invalid)
        assertEquals(
            setOf(ConnectionField.SERVICE_UUID),
            (result as ConnectionValidation.Invalid).errors.keys,
        )
    }

    @Test
    fun bluetoothReportsEveryMalformedIdentifier() {
        val result = ConnectionConfigValidator.validateBluetooth(
            deviceUid = "",
            deviceAddress = "not-a-mac",
            serviceUuid = "service",
            measurementUuid = "measurement",
            commandUuid = "command",
        )

        assertTrue(result is ConnectionValidation.Invalid)
        assertEquals(
            setOf(
                ConnectionField.DEVICE_UID,
                ConnectionField.DEVICE_ADDRESS,
                ConnectionField.SERVICE_UUID,
                ConnectionField.MEASUREMENT_UUID,
                ConnectionField.COMMAND_UUID,
            ),
            (result as ConnectionValidation.Invalid).errors.keys,
        )
    }
}
