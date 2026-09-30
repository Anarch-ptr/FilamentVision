package com.filamentvision.ui.vision

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.filamentvision.input.InputState
import com.filamentvision.vision.processing.PreviewFrame
import com.filamentvision.vision.processing.VisionProcessingResult

@Composable
fun VisionPipelineSection(
    cameraId: String,
    result: VisionProcessingResult?,
    inputState: InputState,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Camera ${cameraId.uppercase()} · ${inputState.label()}", style = MaterialTheme.typography.titleLarge)
        if (result == null) {
            Card(Modifier.fillMaxWidth()) {
                Text("Waiting for a real device frame. No fake image is generated.", Modifier.padding(18.dp))
            }
            return@Column
        }
        PipelineStageCard("Raw", result.rawPreview)
        PipelineStageCard("Grayscale", result.grayscalePreview)
        PipelineStageCard("Threshold · ${result.effectiveThreshold}", result.thresholdPreview)
        PipelineStageCard("Edge", result.edgePreview)
        PipelineStageCard("Detection", result.rawPreview, result)
        MeasurementStageCard(result)
    }
}

@Composable
private fun PipelineStageCard(
    title: String,
    preview: PreviewFrame,
    detection: VisionProcessingResult? = null,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            PreviewCanvas(preview, detection)
        }
    }
}

@Composable
private fun PreviewCanvas(preview: PreviewFrame, detection: VisionProcessingResult?) {
    val image = remember(preview) {
        Bitmap.createBitmap(preview.width, preview.height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(preview.pixels, 0, preview.width, 0, 0, preview.width, preview.height)
        }.asImageBitmap()
    }
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(preview.width.toFloat() / preview.height).background(Color.Black),
    ) {
        drawImage(image)
        detection?.let { result ->
            val scaleX = size.width / result.processedWidth
            val scaleY = size.height / result.processedHeight
            val overlay = result.overlay
            drawRect(
                color = Color(0xFFFFC107),
                topLeft = Offset(overlay.roiLeft * scaleX, overlay.roiTop * scaleY),
                size = androidx.compose.ui.geometry.Size(
                    (overlay.roiRight - overlay.roiLeft) * scaleX,
                    (overlay.roiBottom - overlay.roiTop) * scaleY,
                ),
                style = Stroke(2.dp.toPx()),
            )
            overlay.leftEdge?.let { x ->
                drawLine(Color(0xFF22C55E), Offset(x * scaleX, overlay.roiTop * scaleY), Offset(x * scaleX, overlay.roiBottom * scaleY), 2.dp.toPx())
            }
            overlay.rightEdge?.let { x ->
                drawLine(Color(0xFFEF4444), Offset(x * scaleX, overlay.roiTop * scaleY), Offset(x * scaleX, overlay.roiBottom * scaleY), 2.dp.toPx())
            }
        }
    }
}

@Composable
private fun MeasurementStageCard(result: VisionProcessingResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Measurement", style = MaterialTheme.typography.titleMedium)
            val measurement = result.measurement
            if (measurement == null) {
                Text("No valid width · ${result.error?.name?.replace('_', ' ') ?: "waiting"}")
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${"%.2f".format(measurement.pixelWidth)} px")
                    Text("${"%.3f".format(measurement.diameterMm)} mm")
                    Text("${(measurement.confidence * 100).toInt()}%")
                }
            }
            Text("Frame ${result.sequence} · processing ${result.timings.totalNanos / 1_000_000.0} ms", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun InputState.label(): String = when (this) {
    InputState.Unconfigured -> "UNCONFIGURED"
    is InputState.DriverUnavailable -> "DRIVER UNAVAILABLE"
    InputState.Connecting -> "CONNECTING"
    InputState.Connected -> "CONNECTED"
    InputState.WaitingForFrame -> "WAITING FOR FRAME"
    InputState.Streaming -> "STREAMING"
    InputState.Reconnecting -> "RECONNECTING"
    is InputState.DataFormatError -> "DATA FORMAT ERROR"
    is InputState.ConnectionError -> "CONNECTION ERROR"
    InputState.Stopped -> "STOPPED"
}
