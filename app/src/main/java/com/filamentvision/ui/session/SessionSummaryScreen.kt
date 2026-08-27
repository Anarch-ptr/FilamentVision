package com.filamentvision.ui.session

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filamentvision.model.MonitoringSession
import com.filamentvision.ui.device.DeviceStateRow

@Composable
fun SessionSummaryScreen(
    session: MonitoringSession?,
    onViewDetails: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (session == null) {
        Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
            Text("Session unavailable", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onDone) { Text("Done") }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Session ${session.status.name.lowercase()}", style = MaterialTheme.typography.headlineMedium)
        Text(
            session.endReason ?: "Session finalized",
            color = MaterialTheme.colorScheme.secondary,
        )
        SessionStatisticsCard(session)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onViewDetails, modifier = Modifier.weight(1f)) {
                Text("View Details")
            }
            Button(onClick = onDone, modifier = Modifier.weight(1f)) {
                Text("Done")
            }
        }
    }
}

@Composable
fun SessionStatisticsCard(session: MonitoringSession) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Statistics", fontWeight = FontWeight.Bold)
            DeviceStateRow("Duration", formatSessionDuration(session.durationMillis))
            DeviceStateRow("Samples", session.statistics.sampleCount.toString())
            DeviceStateRow("Average", formatDiameter(session.statistics.averageDiameter))
            DeviceStateRow("Minimum", formatDiameter(session.statistics.minimumDiameter))
            DeviceStateRow("Maximum", formatDiameter(session.statistics.maximumDiameter))
            DeviceStateRow("Std deviation", formatDiameter(session.statistics.standardDeviation))
            DeviceStateRow("Avg confidence", formatConfidence(session.statistics.averageConfidence))
            DeviceStateRow("Abnormal events", session.statistics.abnormalEventCount.toString())
        }
    }
}
