package com.filamentvision.ui.trend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filamentvision.ui.components.chart.DiameterTrendChart

@Composable
fun TrendScreen(viewModel: TrendViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Diameter Trend", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (state.isMonitoring) "LIVE · 8 measurements/s" else "NOT RECORDING · showing saved data",
            color = if (state.isMonitoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendWindow.entries.forEach { window ->
                FilterChip(
                    selected = state.window == window,
                    onClick = { viewModel.selectWindow(window) },
                    label = { Text(window.label) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.showCameraA, viewModel::toggleCameraA, { Text("Camera A") })
            FilterChip(state.showCameraB, viewModel::toggleCameraB, { Text("Camera B") })
            FilterChip(state.showFused, viewModel::toggleFused, { Text("Fused") })
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DiameterTrendChart(
                    points = state.points,
                    alarms = state.alarms,
                    targetDiameter = state.targetDiameter,
                    showCameraA = state.showCameraA,
                    showCameraB = state.showCameraB,
                    showFused = state.showFused,
                    inspectedPoint = state.inspectedPoint,
                    onInspect = viewModel::inspect,
                )
                if (state.points.isEmpty()) Text("No measurements in this window.")
                state.inspectedPoint?.let { point ->
                    Text("Selected: %.3f mm · A %.3f · B %.3f".format(
                        point.fusedDiameter,
                        point.diameterA ?: Float.NaN,
                        point.diameterB ?: Float.NaN,
                    ))
                    val deviation = (point.fusedDiameter - state.targetDiameter) / state.targetDiameter * 100.0
                    val alarmLevel = state.alarms.lastOrNull { it.timestamp <= point.timestamp }?.toLevel?.name ?: "NORMAL"
                    Text(
                        "%tT · deviation %+.2f%% · %s".format(
                            java.util.Date(point.timestamp),
                            deviation,
                            alarmLevel,
                        ),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text("Warning ±2%") })
            AssistChip(onClick = {}, label = { Text("Critical ±5%") })
            if (!state.autoFollow) Button(onClick = viewModel::jumpToLive) { Text("Jump to live") }
        }
    }
}
