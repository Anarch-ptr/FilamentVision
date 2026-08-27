package com.filamentvision.ui.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filamentvision.model.ConnectionState
import com.filamentvision.model.ConnectionConfig
import com.filamentvision.model.MonitoringState
import com.filamentvision.ui.components.CameraPreviewTile
import com.filamentvision.ui.components.ConnectionStatusChip
import com.filamentvision.ui.components.FilamentCore
import com.filamentvision.ui.device.ConnectionDetailsSheet
import java.util.Locale

@Composable
fun MonitorScreen(
    viewModel: MonitorViewModel,
    onOpenDeviceSettings: () -> Unit,
    onOpenCamera: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monitorUiState by viewModel.monitorUiState.collectAsStateWithLifecycle()
    var showConnectionSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        MonitorHeader(
            uiState = monitorUiState,
            onConnectionClick = { showConnectionSheet = true },
        )
        LiveMeasurementPanel(
            viewModel = viewModel,
            monitorUiState = monitorUiState,
            onOpenCamera = onOpenCamera,
        )
    }

    if (showConnectionSheet) {
        ConnectionDetailsSheet(
            uiState = monitorUiState,
            onDismiss = { showConnectionSheet = false },
            onDisconnect = {
                showConnectionSheet = false
                viewModel.disconnect()
            },
            onReconnect = {
                showConnectionSheet = false
                if (monitorUiState.connectionState == ConnectionState.DISCONNECTED) {
                    viewModel.connect()
                } else {
                    viewModel.reconnect()
                }
            },
            onSaveConnection = { config: ConnectionConfig ->
                showConnectionSheet = false
                viewModel.saveConnectionConfig(config)
            },
            onOpenDeviceSettings = {
                showConnectionSheet = false
                onOpenDeviceSettings()
            },
        )
    }
}

@Composable
private fun MonitorHeader(
    uiState: MonitorUiState,
    onConnectionClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("FILAMENT VISION", fontWeight = FontWeight.Bold)
            Text("Dual-view diameter monitor", color = MaterialTheme.colorScheme.secondary)
        }
        ConnectionStatusChip(
            connectionState = uiState.connectionState,
            onClick = onConnectionClick,
        )
    }
}

@Composable
private fun LiveMeasurementPanel(
    viewModel: MonitorViewModel,
    monitorUiState: MonitorUiState,
    onOpenCamera: (String) -> Unit,
) {
    val liveUiState by viewModel.liveMeasurementUiState.collectAsStateWithLifecycle()
    val measurement = liveUiState.latestMeasurement

    if (!monitorUiState.isConnected) {
        DisconnectedMonitorContent(
            connectionState = monitorUiState.connectionState,
            onConnect = if (monitorUiState.canReconnect) {
                if (monitorUiState.connectionState == ConnectionState.DISCONNECTED) {
                    viewModel::connect
                } else {
                    viewModel::reconnect
                }
            } else {
                null
            },
        )
        return
    }

    SessionControls(viewModel = viewModel, uiState = monitorUiState)

    if (measurement == null) {
        Text("Waiting for preview data…", color = MaterialTheme.colorScheme.secondary)
        return
    }

    FilamentCore(
        fusedDiameter = measurement.fusedDiameter,
        status = measurement.status,
    )
    MeasurementSummary(
        difference = measurement.shapeDifference,
        confidence = measurement.confidence,
        bufferedSamples = liveUiState.bufferedSampleCount,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CameraPreviewTile(
            cameraName = "CAMERA A",
            cameraStatus = monitorUiState.cameraAStatus,
            diameter = measurement.diameterA,
            confidence = measurement.cameraAConfidence,
            onClick = { onOpenCamera("A") },
            modifier = Modifier.weight(1f),
        )
        CameraPreviewTile(
            cameraName = "CAMERA B",
            cameraStatus = monitorUiState.cameraBStatus,
            diameter = measurement.diameterB,
            confidence = measurement.cameraBConfidence,
            onClick = { onOpenCamera("B") },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SessionControls(viewModel: MonitorViewModel, uiState: MonitorUiState) {
    when (uiState.monitoringState) {
        MonitoringState.READY -> {
            Text("Connected · Ready", color = MaterialTheme.colorScheme.primary)
            Button(
                onClick = viewModel::startMonitoring,
                enabled = uiState.canStartMonitoring,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start Monitoring")
            }
        }
        MonitoringState.MONITORING -> {
            val clock by viewModel.sessionClockUiState.collectAsStateWithLifecycle()
            Text(
                text = "Monitoring · ${formatElapsedTime(clock.elapsedMillis)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(
                onClick = viewModel::stopMonitoring,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Stop Monitoring")
            }
        }
        MonitoringState.STARTING -> Text("Starting monitoring…")
        MonitoringState.STOPPING -> Text("Finalizing session…")
        MonitoringState.COMPLETED -> Text("Session completed")
        MonitoringState.INTERRUPTED -> Text("Session interrupted", color = MaterialTheme.colorScheme.error)
        MonitoringState.IDLE -> Text("Preparing monitor…")
    }
}

private fun formatElapsedTime(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun DisconnectedMonitorContent(
    connectionState: ConnectionState,
    onConnect: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.padding(vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = when (connectionState) {
                ConnectionState.ERROR -> "Connection lost"
                ConnectionState.DISCONNECTED -> "Monitor disconnected"
                else -> "Preparing monitor…"
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "Measurement and camera updates are stopped.",
            color = MaterialTheme.colorScheme.secondary,
        )
        if (onConnect != null) {
            Button(onClick = onConnect) {
                Text(if (connectionState == ConnectionState.ERROR) "Reconnect" else "Connect")
            }
        }
    }
}

@Composable
private fun MeasurementSummary(
    difference: Double,
    confidence: Double,
    bufferedSamples: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SummaryMetric("Difference", String.format(Locale.US, "%.3f mm", difference))
        SummaryMetric("Confidence", String.format(Locale.US, "%.0f%%", confidence * 100))
        SummaryMetric("Buffer", "$bufferedSamples / 300")
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MaterialTheme.colorScheme.secondary)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
