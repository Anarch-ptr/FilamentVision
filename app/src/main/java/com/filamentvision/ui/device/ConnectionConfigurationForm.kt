package com.filamentvision.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.filamentvision.domain.connection.ConnectionConfigValidator
import com.filamentvision.domain.connection.ConnectionField
import com.filamentvision.domain.connection.ConnectionValidation
import com.filamentvision.model.BluetoothConnectionConfig
import com.filamentvision.model.ConnectionConfig
import com.filamentvision.model.ConnectionType
import com.filamentvision.model.WifiConnectionConfig

@Composable
fun ConnectionConfigurationForm(
    type: ConnectionType,
    initialConfig: ConnectionConfig,
    onCancel: () -> Unit,
    onSave: (ConnectionConfig) -> Unit,
) {
    val wifi = initialConfig as? WifiConnectionConfig
    val bluetooth = initialConfig as? BluetoothConnectionConfig
    var deviceUid by remember(type, initialConfig) { mutableStateOf(initialConfig.deviceUid) }
    var host by remember(type, initialConfig) { mutableStateOf(wifi?.host.orEmpty()) }
    var port by remember(type, initialConfig) { mutableStateOf(wifi?.port?.toString().orEmpty()) }
    var address by remember(type, initialConfig) { mutableStateOf(bluetooth?.deviceAddress.orEmpty()) }
    var serviceUuid by remember(type, initialConfig) { mutableStateOf(bluetooth?.serviceUuid.orEmpty()) }
    var measurementUuid by remember(type, initialConfig) {
        mutableStateOf(bluetooth?.measurementCharacteristicUuid.orEmpty())
    }
    var commandUuid by remember(type, initialConfig) {
        mutableStateOf(bluetooth?.commandCharacteristicUuid.orEmpty())
    }
    var errors by remember(type) { mutableStateOf<Map<ConnectionField, String>>(emptyMap()) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (type == ConnectionType.WIFI) "Wi-Fi parameters" else "Bluetooth BLE parameters")
        ConfigField("Device UID", deviceUid, { deviceUid = it }, errors[ConnectionField.DEVICE_UID])
        if (type == ConnectionType.WIFI) {
            ConfigField("Host / IP", host, { host = it }, errors[ConnectionField.HOST])
            ConfigField(
                label = "Port",
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                error = errors[ConnectionField.PORT],
                keyboardType = KeyboardType.Number,
            )
        } else {
            ConfigField("Device address / MAC", address, { address = it }, errors[ConnectionField.DEVICE_ADDRESS])
            ConfigField("Service UUID", serviceUuid, { serviceUuid = it }, errors[ConnectionField.SERVICE_UUID])
            ConfigField(
                "Measurement characteristic UUID",
                measurementUuid,
                { measurementUuid = it },
                errors[ConnectionField.MEASUREMENT_UUID],
            )
            ConfigField(
                "Command characteristic UUID",
                commandUuid,
                { commandUuid = it },
                errors[ConnectionField.COMMAND_UUID],
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = {
                    val result = if (type == ConnectionType.WIFI) {
                        ConnectionConfigValidator.validateWifi(deviceUid, host, port)
                    } else {
                        ConnectionConfigValidator.validateBluetooth(
                            deviceUid,
                            address,
                            serviceUuid,
                            measurementUuid,
                            commandUuid,
                        )
                    }
                    when (result) {
                        is ConnectionValidation.Valid -> onSave(result.config)
                        is ConnectionValidation.Invalid -> errors = result.errors
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Save & reconnect") }
        }
        Text("Fake configuration only; no radio or socket is opened.")
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
