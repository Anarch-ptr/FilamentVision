package com.filamentvision.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filamentvision.model.SimulationScenario
import com.filamentvision.ui.monitor.MonitorViewModel

@Composable
fun DiagnosticsScreen(
    viewModel: MonitorViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.monitorUiState.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnosticsUiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Diagnostics / Simulation", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Manual fake scenarios for product-state testing. No hardware API is used.",
            color = MaterialTheme.colorScheme.secondary,
        )
        DeviceStateRow("Connection", uiState.connectionState.name.replace('_', ' '))
        DeviceStateRow("Monitoring", uiState.monitoringState.name.replace('_', ' '))
        DeviceStateRow("Active producer", diagnostics.activeProducerCount.toString())
        DeviceStateRow("Total producer starts", diagnostics.totalProducerStarts.toString())
        DeviceStateRow("Measurements", diagnostics.emittedMeasurementCount.toString())
        DeviceStateRow("Realtime buffer", "${diagnostics.realtimeBufferSize} / 300")
        DeviceStateRow("Selected scenario", uiState.selectedScenario.displayName)

        SimulationScenario.entries.forEach { scenario ->
            val isSelected = scenario == uiState.selectedScenario
            if (isSelected) {
                Button(
                    onClick = { viewModel.selectSimulationScenario(scenario) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(scenario.displayName)
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.selectSimulationScenario(scenario) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(scenario.displayName)
                }
            }
        }
    }
}
