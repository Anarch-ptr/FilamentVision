package com.filamentvision.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filamentvision.model.CalibrationStatus
import com.filamentvision.model.ConnectionState
import com.filamentvision.model.ConnectionType
import com.filamentvision.model.ConnectionConfig
import com.filamentvision.model.BluetoothConnectionConfig
import com.filamentvision.model.WifiConnectionConfig
import com.filamentvision.ui.monitor.MonitorViewModel

@Composable
fun DeviceScreen(
    viewModel: MonitorViewModel,
    onOpenDiagnostics: () -> Unit,
    onOpenCamera: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.monitorUiState.collectAsStateWithLifecycle()
    var showConnectionSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Device", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = uiState.deviceName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        DeviceInformationCard(uiState = uiState)
        MonitoringConfigurationCard(uiState = uiState)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = { onOpenCamera("A") }, modifier = Modifier.weight(1f)) {
                Text("Camera A")
            }
            OutlinedButton(onClick = { onOpenCamera("B") }, modifier = Modifier.weight(1f)) {
                Text("Camera B")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    if (uiState.connectionState == ConnectionState.DISCONNECTED) {
                        viewModel.connect()
                    } else {
                        viewModel.reconnect()
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (uiState.canReconnect) "Connect" else "Reconnect")
            }
            OutlinedButton(
                onClick = viewModel::disconnect,
                enabled = uiState.canDisconnect,
                modifier = Modifier.weight(1f),
            ) {
                Text("Disconnect")
            }
        }
        OutlinedButton(
            onClick = { showConnectionSheet = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Change connection")
        }
        OutlinedButton(
            onClick = viewModel::runFakeCalibration,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (uiState.calibrationStatus == CalibrationStatus.CALIBRATING) "Calibrating…" else "Calibration")
        }
        OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text("Diagnostics / Simulation")
        }
    }

    if (showConnectionSheet) {
        ConnectionDetailsSheet(
            uiState = uiState,
            onDismiss = { showConnectionSheet = false },
            onDisconnect = {
                showConnectionSheet = false
                viewModel.disconnect()
            },
            onReconnect = {
                showConnectionSheet = false
                if (uiState.connectionState == ConnectionState.DISCONNECTED) {
                    viewModel.connect()
                } else {
                    viewModel.reconnect()
                }
            },
            onSaveConnection = { config: ConnectionConfig ->
                showConnectionSheet = false
                viewModel.saveConnectionConfig(config)
            },
            onOpenDeviceSettings = { showConnectionSheet = false },
        )
    }
}

@Composable
private fun DeviceInformationCard(uiState: com.filamentvision.ui.monitor.MonitorUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DeviceStateRow("Connection", uiState.connectionState.name.replace('_', ' '))
            DeviceStateRow("Monitoring", uiState.monitoringState.name.replace('_', ' '))
            DeviceStateRow("Method", uiState.connectionType.displayName())
            DeviceStateRow("Device UID", uiState.connectionSettings.active.deviceUid)
            when (val config = uiState.connectionSettings.active) {
                is WifiConnectionConfig -> DeviceStateRow("Endpoint", "${config.host}:${config.port}")
                is BluetoothConnectionConfig -> DeviceStateRow("BLE address", config.deviceAddress)
            }
            DeviceStateRow("Camera A", uiState.cameraAStatus.name.replace('_', ' '))
            DeviceStateRow("Camera B", uiState.cameraBStatus.name.replace('_', ' '))
            DeviceStateRow("Calibration", uiState.calibrationStatus.name.replace('_', ' '))
        }
    }
}

@Composable
private fun MonitoringConfigurationCard(uiState: com.filamentvision.ui.monitor.MonitorUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Monitoring configuration", fontWeight = FontWeight.SemiBold)
            DeviceStateRow("Target diameter", "%.3f mm".format(uiState.configuration.targetDiameterMm))
            DeviceStateRow("Measurement rate", "${uiState.configuration.measurementRateHz} Hz")
            DeviceStateRow("Realtime buffer", "${uiState.configuration.realtimeBufferCapacity} samples")
            DeviceStateRow("Fake producers", uiState.activeFakeProducerCount.toString())
        }
    }
}

private fun ConnectionType.displayName(): String = when (this) {
    ConnectionType.BLUETOOTH -> "Bluetooth"
    ConnectionType.WIFI -> "Wi-Fi"
}
