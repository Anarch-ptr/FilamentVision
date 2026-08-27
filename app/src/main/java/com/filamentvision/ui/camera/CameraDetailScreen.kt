package com.filamentvision.ui.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.filamentvision.ui.monitor.MonitorViewModel
import com.filamentvision.ui.session.formatConfidence
import com.filamentvision.ui.session.formatDiameter

@Composable
fun CameraDetailScreen(
    cameraId: String,
    viewModel: MonitorViewModel,
    modifier: Modifier = Modifier,
) {
    val monitorState by viewModel.monitorUiState.collectAsStateWithLifecycle()
    val liveState by viewModel.liveMeasurementUiState.collectAsStateWithLifecycle()
    var showDetection by remember { mutableStateOf(false) }
    val isCameraA = cameraId.equals("A", ignoreCase = true)
    val cameraStatus = if (isCameraA) monitorState.cameraAStatus else monitorState.cameraBStatus
    val measurement = liveState.latestMeasurement

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Camera ${if (isCameraA) "A" else "B"}", style = MaterialTheme.typography.headlineMedium)
        Text(cameraStatus.name.replace('_', ' '), color = MaterialTheme.colorScheme.secondary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!showDetection) {
                Button(onClick = { showDetection = false }, modifier = Modifier.weight(1f)) { Text("Original") }
                OutlinedButton(onClick = { showDetection = true }, modifier = Modifier.weight(1f)) { Text("Detection") }
            } else {
                OutlinedButton(onClick = { showDetection = false }, modifier = Modifier.weight(1f)) { Text("Original") }
                Button(onClick = { showDetection = true }, modifier = Modifier.weight(1f)) { Text("Detection") }
            }
        }
        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(if (showDetection) "Fake edge detection preview" else "Fake original camera preview")
                Text("No camera hardware is connected.", color = MaterialTheme.colorScheme.secondary)
            }
        }
        measurement?.let {
            val diameter = if (isCameraA) it.diameterA else it.diameterB
            val confidence = if (isCameraA) it.cameraAConfidence else it.cameraBConfidence
            Text(formatDiameter(diameter), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Confidence ${formatConfidence(confidence)}")
        }
    }
}
