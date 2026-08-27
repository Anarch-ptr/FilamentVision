package com.filamentvision.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filamentvision.model.BluetoothConnectionConfig
import com.filamentvision.model.ConnectionConfig
import com.filamentvision.model.ConnectionState
import com.filamentvision.model.ConnectionType
import com.filamentvision.model.WifiConnectionConfig
import com.filamentvision.ui.monitor.MonitorUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDetailsSheet(
    uiState: MonitorUiState,
    onDismiss: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onSaveConnection: (ConnectionConfig) -> Unit,
    onOpenDeviceSettings: () -> Unit,
) {
    var editingType by remember { mutableStateOf<ConnectionType?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Connection", style = MaterialTheme.typography.headlineSmall)
            if (editingType != null) {
                val currentType = checkNotNull(editingType)
                ConnectionConfigurationForm(
                    type = currentType,
                    initialConfig = when (currentType) {
                        ConnectionType.WIFI -> uiState.connectionSettings.wifi
                        ConnectionType.BLUETOOTH -> uiState.connectionSettings.bluetooth
                    },
                    onCancel = { editingType = null },
                    onSave = onSaveConnection,
                )
            } else {
                ConnectionSummary(uiState)
                HorizontalDivider()
                if (uiState.canReconnect) {
                    Button(onClick = onReconnect, modifier = Modifier.fillMaxWidth()) {
                        Text(if (uiState.connectionState == ConnectionState.DISCONNECTED) "Connect" else "Reconnect")
                    }
                }
                if (uiState.canDisconnect) {
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                        Text("Disconnect")
                    }
                }
                Text("Configure connection", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConnectionChoiceButton("Wi-Fi", { editingType = ConnectionType.WIFI }, Modifier.weight(1f))
                    ConnectionChoiceButton("Bluetooth", { editingType = ConnectionType.BLUETOOTH }, Modifier.weight(1f))
                }
                OutlinedButton(onClick = onOpenDeviceSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Device settings")
                }
            }
        }
    }
}

@Composable
private fun ConnectionSummary(uiState: MonitorUiState) {
    DeviceStateRow("Device", uiState.deviceName)
    DeviceStateRow("Method", uiState.connectionType.displayName())
    DeviceStateRow("State", uiState.connectionState.name.replace('_', ' '))
    DeviceStateRow("Device UID", uiState.connectionSettings.active.deviceUid)
    when (val config = uiState.connectionSettings.active) {
        is WifiConnectionConfig -> DeviceStateRow("Endpoint", "${config.host}:${config.port}")
        is BluetoothConnectionConfig -> {
            DeviceStateRow("BLE address", config.deviceAddress)
            DeviceStateRow("Service UUID", config.serviceUuid)
        }
    }
    DeviceStateRow("Camera A", uiState.cameraAStatus.name.replace('_', ' '))
    DeviceStateRow("Camera B", uiState.cameraBStatus.name.replace('_', ' '))
}

@Composable
fun DeviceStateRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.secondary)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConnectionChoiceButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) { Text(label) }
}

private fun ConnectionType.displayName(): String = when (this) {
    ConnectionType.BLUETOOTH -> "Bluetooth"
    ConnectionType.WIFI -> "Wi-Fi"
}
