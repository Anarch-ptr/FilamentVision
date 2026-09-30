package com.filamentvision.ui.error

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filamentvision.domain.error.ErrorEvent
import com.filamentvision.domain.error.ErrorState
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ErrorLogScreen(viewModel: ErrorLogViewModel, onOpenError: (Long) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("System Errors", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ErrorFilter.entries.forEach { filter ->
                if (state.filter == filter) Button({ viewModel.selectFilter(filter) }) { Text(filter.name) }
                else OutlinedButton({ viewModel.selectFilter(filter) }) { Text(filter.name) }
            }
        }
        if (state.errors.isEmpty()) Text("No matching error episodes.", color = MaterialTheme.colorScheme.secondary)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.errors, key = ErrorEvent::id) { event -> ErrorEpisodeCard(event) { onOpenError(event.id) } }
        }
        OutlinedButton(viewModel::clearResolved, modifier = Modifier.fillMaxWidth()) { Text("Clear resolved errors") }
    }
}

@Composable
private fun ErrorEpisodeCard(event: ErrorEvent, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(event.severity.name, fontWeight = FontWeight.Bold)
                Text(event.state.name)
            }
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            Text(event.code, color = MaterialTheme.colorScheme.secondary)
            Text("${formatTimestamp(event.lastTimestamp)} · ${event.category.name.replace('_', ' ')}")
            if (event.occurrenceCount > 1) Text("Occurred ${event.occurrenceCount} times")
        }
    }
}

@Composable
fun ErrorDetailScreen(viewModel: ErrorLogViewModel, errorId: Long, onOpenSnapshot: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.detail.collectAsStateWithLifecycle()
    LaunchedEffect(errorId) { viewModel.loadError(errorId) }
    val event = state.event
    if (event == null) { Column(modifier.fillMaxSize().padding(20.dp)) { Text(if (state.loading) "Loading error…" else "Error unavailable") }; return }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(event.title, style = MaterialTheme.typography.headlineMedium) }
        item { ErrorFields(event) }
        item { Text("Error snapshots", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.snapshots.isEmpty()) item { Text("Snapshot unavailable", color = MaterialTheme.colorScheme.secondary) }
        items(state.snapshots, key = { it.id }) { snapshot ->
            val bitmap = rememberDecodedImage(snapshot.filePath, 512)
            Card(Modifier.fillMaxWidth().clickable { onOpenSnapshot(snapshot.filePath) }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Camera ${snapshot.cameraId ?: "—"} · ${formatTimestamp(snapshot.timestamp)}")
                    if (bitmap != null) Image(bitmap, null, Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit)
                    else Text("Snapshot file unavailable")
                }
            }
        }
        if (event.state == ErrorState.RESOLVED) item {
            OutlinedButton({ viewModel.deleteResolved(event.id) }, Modifier.fillMaxWidth()) { Text("Delete resolved error") }
        }
    }
}

@Composable
private fun ErrorFields(event: ErrorEvent) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf(
            "Severity" to event.severity.name, "Status" to event.state.name, "Error code" to event.code,
            "Category" to event.category.name, "First seen" to formatTimestamp(event.firstTimestamp),
            "Last seen" to formatTimestamp(event.lastTimestamp), "Occurrences" to event.occurrenceCount.toString(),
            "Component" to (event.component ?: "—"), "Session" to (event.sessionId ?: "—"),
            "Camera" to (event.cameraId ?: "—"), "Resolved" to (event.resolvedAt?.let(::formatTimestamp) ?: "—"),
        ).forEach { (label, value) -> Text("$label  $value") }
        Text(event.message, color = MaterialTheme.colorScheme.secondary)
    } }
}

@Composable
fun ErrorSnapshotViewer(filePath: String, modifier: Modifier = Modifier) {
    val bitmap = rememberDecodedImage(filePath, 2048)
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        if (bitmap != null) Image(bitmap, "Error snapshot", Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
        else Text("Snapshot file unavailable")
    }
}

@Composable
private fun rememberDecodedImage(filePath: String, maxDimension: Int): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, filePath, maxDimension) {
        value = withContext(Dispatchers.IO) { decodeSampled(filePath, maxDimension)?.asImageBitmap() }
    }
    return bitmap
}

private fun decodeSampled(filePath: String, maxDimension: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(filePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension * 2) sample *= 2
    return BitmapFactory.decodeFile(filePath, BitmapFactory.Options().apply { inSampleSize = sample })
}

private fun formatTimestamp(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(timestamp))
