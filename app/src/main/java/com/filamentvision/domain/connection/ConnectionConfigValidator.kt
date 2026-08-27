package com.filamentvision.domain.connection

import com.filamentvision.model.BluetoothConnectionConfig
import com.filamentvision.model.ConnectionConfig
import com.filamentvision.model.WifiConnectionConfig
import java.util.UUID

enum class ConnectionField {
    DEVICE_UID,
    HOST,
    PORT,
    DEVICE_ADDRESS,
    SERVICE_UUID,
    MEASUREMENT_UUID,
    COMMAND_UUID,
}

sealed interface ConnectionValidation {
    data class Valid(val config: ConnectionConfig) : ConnectionValidation
    data class Invalid(val errors: Map<ConnectionField, String>) : ConnectionValidation
}

object ConnectionConfigValidator {
    fun validateWifi(deviceUid: String, host: String, port: String): ConnectionValidation {
        val normalizedUid = deviceUid.trim()
        val normalizedHost = host.trim()
        val parsedPort = port.trim().toIntOrNull()
        val errors = buildMap {
            if (normalizedUid.isEmpty()) put(ConnectionField.DEVICE_UID, "Device UID is required")
            if (normalizedHost.isEmpty()) put(ConnectionField.HOST, "Host or IP is required")
            if (parsedPort == null || parsedPort !in 1..65_535) {
                put(ConnectionField.PORT, "Port must be between 1 and 65535")
            }
        }
        return if (errors.isEmpty()) {
            ConnectionValidation.Valid(WifiConnectionConfig(normalizedUid, normalizedHost, checkNotNull(parsedPort)))
        } else {
            ConnectionValidation.Invalid(errors)
        }
    }

    fun validateBluetooth(
        deviceUid: String,
        deviceAddress: String,
        serviceUuid: String,
        measurementUuid: String,
        commandUuid: String,
    ): ConnectionValidation {
        val normalizedUid = deviceUid.trim()
        val normalizedAddress = deviceAddress.trim().replace('-', ':').uppercase()
        val normalizedService = serviceUuid.trim().lowercase()
        val normalizedMeasurement = measurementUuid.trim().lowercase()
        val normalizedCommand = commandUuid.trim().lowercase()
        val errors = buildMap {
            if (normalizedUid.isEmpty()) put(ConnectionField.DEVICE_UID, "Device UID is required")
            if (!MAC_ADDRESS.matches(normalizedAddress)) {
                put(ConnectionField.DEVICE_ADDRESS, "Use a Bluetooth address such as AA:BB:CC:DD:EE:FF")
            }
            if (!isUuid(normalizedService)) put(ConnectionField.SERVICE_UUID, "Invalid Service UUID")
            if (!isUuid(normalizedMeasurement)) put(ConnectionField.MEASUREMENT_UUID, "Invalid Measurement UUID")
            if (!isUuid(normalizedCommand)) put(ConnectionField.COMMAND_UUID, "Invalid Command UUID")
        }
        return if (errors.isEmpty()) {
            ConnectionValidation.Valid(
                BluetoothConnectionConfig(
                    normalizedUid,
                    normalizedAddress,
                    normalizedService,
                    normalizedMeasurement,
                    normalizedCommand,
                ),
            )
        } else {
            ConnectionValidation.Invalid(errors)
        }
    }

    private fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
    private val MAC_ADDRESS = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
}
