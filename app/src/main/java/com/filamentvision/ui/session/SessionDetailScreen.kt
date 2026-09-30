package com.filamentvision.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filamentvision.data.repository.PersistedAlarmEvent
import com.filamentvision.domain.error.ErrorEvent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filamentvision.ui.components.chart.DiameterTrendChart
import com.filamentvision.ui.history.HistoryViewModel
import java.text.DateFormat
import java.util.Date

private sealed interface SessionTimelineItem {
    val timestamp: Long
    data class Alarm(val event: PersistedAlarmEvent) : SessionTimelineItem { override val timestamp = event.timestamp }
    data class SystemError(val event: ErrorEvent) : SessionTimelineItem { override val timestamp = event.firstTimestamp }
}

@Composable
fun SessionDetailScreen(sessionId: String, viewModel: HistoryViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.selected.collectAsStateWithLifecycle()
    LaunchedEffect(sessionId) { viewModel.loadSession(sessionId) }
    val session = state.session
    if (session == null) {
        Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
            Text(if (state.isLoading) "Loading session…" else "Session unavailable", style = MaterialTheme.typography.headlineMedium)
        }
        return
    }
    val timeline = (state.alarms.map(SessionTimelineItem::Alarm) + state.errors.map(SessionTimelineItem::SystemError))
        .sortedBy(SessionTimelineItem::timestamp)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Session Details", style = MaterialTheme.typography.headlineMedium)
                Text(formatSessionDate(session.startedAt), color = MaterialTheme.colorScheme.secondary)
                Text("Target ${formatDiameter(session.targetDiameter)} · ${session.status.name}")
            }
        }
        item { SessionStatisticsCard(session) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                DiameterTrendChart(
                    points = state.points,
                    alarms = state.alarms,
                    targetDiameter = session.targetDiameter,
                    showCameraA = true,
                    showCameraB = true,
                    showFused = true,
                    inspectedPoint = null,
                    onInspect = {},
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        item { Text("Session events", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (timeline.isEmpty()) item { Text("No alarms or system errors recorded.", color = MaterialTheme.colorScheme.secondary) }
        else items(timeline, key = { item -> when (item) {
            is SessionTimelineItem.Alarm -> "alarm-${item.event.id}"
            is SessionTimelineItem.SystemError -> "error-${item.event.id}"
        } }) { item -> when (item) {
            is SessionTimelineItem.Alarm -> AlarmEventCard(item.event)
            is SessionTimelineItem.SystemError -> SystemErrorCard(item.event)
        } }
    }
}

@Composable
private fun SystemErrorCard(error: ErrorEvent) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${error.severity.name} · ${error.title}", fontWeight = FontWeight.SemiBold)
            Text("System error · ${formatEventTime(error.firstTimestamp)}")
            Text(error.code, color = MaterialTheme.colorScheme.secondary)
            Text("${error.state.name} · occurred ${error.occurrenceCount} time(s)")
        }
    }
}

@Composable
private fun AlarmEventCard(event: PersistedAlarmEvent) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${event.fromLevel.name} → ${event.toLevel.name}", fontWeight = FontWeight.SemiBold)
            Text("Diameter alarm · ${formatEventTime(event.timestamp)}")
            Text(event.description, color = MaterialTheme.colorScheme.secondary)
            Text("Diameter ${formatDiameter(event.fusedDiameter)} · deviation ${"%.2f".format(event.deviationPercent * 100)}%")
        }
    }
}

private fun formatEventTime(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(timestamp))
