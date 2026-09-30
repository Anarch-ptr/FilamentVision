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
import com.filamentvision.model.ConnectionProfile
import com.filamentvision.ui.monitor.MonitorViewModel
import com.filamentvision.ui.error.ErrorLogViewModel
import com.filamentvision.domain.error.ErrorSeverity
import com.filamentvision.domain.error.ErrorState

@Composable
fun DeviceScreen(
    viewModel: MonitorViewModel,
    errorViewModel: ErrorLogViewModel,
    onOpenDiagnostics: () -> Unit,
    onOpenVisionDiagnostics: () -> Unit,
    onOpenErrors: () -> Unit,
    onOpenCamera: (String) -> Unit,
    onOpenCalibration: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.monitorUiState.collectAsStateWithLifecycle()
    val errors by errorViewModel.state.collectAsStateWithLifecycle()
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
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val active = errors.errors.count { it.state == ErrorState.ACTIVE }
                val critical = errors.errors.count { it.state == ErrorState.ACTIVE && it.severity == ErrorSeverity.CRITICAL }
                Text("System health", fontWeight = FontWeight.SemiBold)
                Text(if (active == 0) "No active errors" else "$active active errors · $critical critical")
                OutlinedButton(onOpenErrors, Modifier.fillMaxWidth()) { Text("View Error Log") }
            }
        }

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
        Text("Calibration uses live real-input preview. Save persists; Apply activates the revision.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onOpenCalibration("A") }, modifier = Modifier.weight(1f)) { Text("Calibrate A") }
            OutlinedButton(onClick = { onOpenCalibration("B") }, modifier = Modifier.weight(1f)) { Text("Calibrate B") }
        }
        OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text("Diagnostics")
        }
        OutlinedButton(onClick = onOpenVisionDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text("Vision Pipeline Diagnostics")
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
            onSaveConnection = { profile: ConnectionProfile ->
                showConnectionSheet = false
                viewModel.saveConnectionProfile(profile)
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
            DeviceStateRow("Input", uiState.inputState.label())
            DeviceStateRow("Topology", uiState.connectionProfile.topology.name.replace('_', ' '))
            DeviceStateRow("Endpoints", uiState.connectionProfile.endpoints.size.toString())
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
            DeviceStateRow("Active producers", uiState.activeProducerCount.toString())
        }
    }
}
