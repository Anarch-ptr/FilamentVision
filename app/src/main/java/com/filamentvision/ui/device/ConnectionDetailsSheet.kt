package com.filamentvision.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.filamentvision.input.InputState
import com.filamentvision.model.BluetoothEndpointProfile
import com.filamentvision.model.ConnectionProfile
import com.filamentvision.model.ConnectionState
import com.filamentvision.model.WifiEndpointProfile
import com.filamentvision.ui.monitor.MonitorUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDetailsSheet(
    uiState: MonitorUiState,
    onDismiss: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onSaveConnection: (ConnectionProfile) -> Unit,
    onOpenDeviceSettings: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Connection", style = MaterialTheme.typography.headlineSmall)
            if (editing) {
                ConnectionProfileForm(
                    initialProfile = uiState.connectionProfile,
                    onCancel = { editing = false },
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
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
                }
                Button(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Edit connection profile") }
                OutlinedButton(onClick = onOpenDeviceSettings, modifier = Modifier.fillMaxWidth()) { Text("Open Device settings") }
            }
        }
    }
}

@Composable
private fun ConnectionSummary(uiState: MonitorUiState) {
    DeviceStateRow("Device", uiState.deviceName)
    DeviceStateRow("Input state", uiState.inputState.label())
    DeviceStateRow("Topology", uiState.connectionProfile.topology.name.replace('_', ' '))
    uiState.connectionProfile.endpoints.forEach { endpoint ->
        DeviceStateRow("Endpoint ${endpoint.endpointId}", when (endpoint) {
            is WifiEndpointProfile -> "${endpoint.protocol} ${endpoint.host}:${endpoint.port}"
            is BluetoothEndpointProfile -> "${endpoint.protocol} ${endpoint.deviceName.ifBlank { endpoint.deviceUid }}"
        })
    }
    uiState.connectionProfile.cameras.forEach { camera ->
        DeviceStateRow("Camera ${camera.cameraId}", "${camera.endpointId} · ${camera.cameraUid.ifBlank { "UID not set" }}")
    }
    DeviceStateRow("Camera A", uiState.cameraAStatus.name.replace('_', ' '))
    DeviceStateRow("Camera B", uiState.cameraBStatus.name.replace('_', ' '))
}

@Composable
fun DeviceStateRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.secondary)
        Text(value, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}

fun InputState.label(): String = when (this) {
    InputState.Unconfigured -> "UNCONFIGURED"
    is InputState.DriverUnavailable -> "DRIVER UNAVAILABLE"
    InputState.Connecting -> "CONNECTING"
    InputState.Connected -> "CONNECTED"
    InputState.WaitingForFrame -> "WAITING FOR FRAME"
    InputState.Streaming -> "STREAMING"
    InputState.Reconnecting -> "RECONNECTING"
    is InputState.DataFormatError -> "DATA FORMAT ERROR"
    is InputState.ConnectionError -> "CONNECTION ERROR"
    InputState.Stopped -> "STOPPED"
}
