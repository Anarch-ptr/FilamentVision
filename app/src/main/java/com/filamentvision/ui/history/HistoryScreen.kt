package com.filamentvision.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filamentvision.model.MonitoringSession
import com.filamentvision.ui.session.formatDiameter
import com.filamentvision.ui.session.formatSessionDate
import com.filamentvision.ui.session.formatSessionDuration

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    var pendingDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<MonitoringSession?>(null) }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete session?") },
            text = { Text("Measurements and alarm events for this session will also be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(session.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    if (sessions.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("History", style = MaterialTheme.typography.headlineMedium)
            Text("Completed sessions will appear here.", color = MaterialTheme.colorScheme.secondary)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("History", style = MaterialTheme.typography.headlineMedium) }
        items(items = sessions, key = MonitoringSession::id) { session ->
            SessionHistoryCard(
                session = session,
                onClick = { onOpenSession(session.id) },
                onDelete = { pendingDelete = session },
            )
        }
    }
}

@Composable
private fun SessionHistoryCard(session: MonitoringSession, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(session.status.name, fontWeight = FontWeight.Bold)
            Text(formatSessionDate(session.startedAt), color = MaterialTheme.colorScheme.secondary)
            Text("Duration ${formatSessionDuration(session.durationMillis)}")
            Text(
                "${session.statistics.sampleCount} samples · Avg ${formatDiameter(session.statistics.averageDiameter)}",
            )
            Text("${session.statistics.abnormalEventCount} abnormal events")
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}
