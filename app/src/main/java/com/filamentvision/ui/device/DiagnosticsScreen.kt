package com.filamentvision.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filamentvision.ui.monitor.MonitorViewModel
import com.filamentvision.ui.error.ErrorLogViewModel

@Composable
fun DiagnosticsScreen(
    viewModel: MonitorViewModel,
    errorViewModel: ErrorLogViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.monitorUiState.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnosticsUiState.collectAsStateWithLifecycle()
    val snapshotDiagnostics by errorViewModel.diagnostics.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { errorViewModel.refreshDiagnostics() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Input diagnostics", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Live production state. Unavailable drivers never report a successful connection.",
            color = MaterialTheme.colorScheme.secondary,
        )
        DeviceStateRow("Connection", uiState.connectionState.name.replace('_', ' '))
        DeviceStateRow("Input", uiState.inputState.displayName())
        DeviceStateRow("Monitoring", uiState.monitoringState.name.replace('_', ' '))
        DeviceStateRow("Active producer", diagnostics.activeProducerCount.toString())
        DeviceStateRow("Total producer starts", diagnostics.totalProducerStarts.toString())
        DeviceStateRow("Measurements", diagnostics.emittedMeasurementCount.toString())
        DeviceStateRow("Realtime buffer", "${diagnostics.realtimeBufferSize} / 300")
        DeviceStateRow("Error snapshots", snapshotDiagnostics.count.toString())
        DeviceStateRow("Snapshot storage", "${snapshotDiagnostics.totalBytes / 1024} KiB")
    }
}

private fun com.filamentvision.input.InputState.displayName(): String = when (this) {
    com.filamentvision.input.InputState.Unconfigured -> "UNCONFIGURED"
    is com.filamentvision.input.InputState.DriverUnavailable ->
        "DRIVER UNAVAILABLE (${protocols.joinToString { it.name }})"
    com.filamentvision.input.InputState.Connecting -> "CONNECTING"
    com.filamentvision.input.InputState.Connected -> "CONNECTED"
    com.filamentvision.input.InputState.WaitingForFrame -> "WAITING FOR FRAME"
    com.filamentvision.input.InputState.Streaming -> "STREAMING"
    com.filamentvision.input.InputState.Reconnecting -> "RECONNECTING"
    is com.filamentvision.input.InputState.DataFormatError -> "DATA FORMAT ERROR: $message"
    is com.filamentvision.input.InputState.ConnectionError -> "CONNECTION ERROR: $message"
    com.filamentvision.input.InputState.Stopped -> "STOPPED"
}
