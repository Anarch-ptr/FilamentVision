package com.filamentvision.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filamentvision.model.CameraStatus
import com.filamentvision.vision.processing.VisionProcessingResult
import java.util.Locale

@Composable
fun CameraPreviewTile(
    cameraName: String,
    cameraStatus: CameraStatus,
    diameter: Double,
    confidence: Double,
    result: VisionProcessingResult?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick, modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(cameraName, fontWeight = FontWeight.Bold)
                Text(cameraStatus.name.replace('_', ' '))
            }
            Text(if (diameter.isFinite()) String.format(Locale.US, "%.3f mm", diameter) else "— mm")
            val preview = result?.rawPreview
            if (preview == null) {
                Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Color.Black))
                Text("WAITING FOR REAL DEVICE FRAME", style = MaterialTheme.typography.labelSmall)
            } else {
                val bitmap = remember(preview) {
                    Bitmap.createBitmap(preview.width, preview.height, Bitmap.Config.ARGB_8888).also {
                        it.setPixels(preview.pixels, 0, preview.width, 0, 0, preview.width, preview.height)
                    }.asImageBitmap()
                }
                Image(bitmap, cameraName, Modifier.fillMaxWidth().aspectRatio(preview.width.toFloat() / preview.height), contentScale = ContentScale.Fit)
                Text("Frame ${result.sequence} · real input", style = MaterialTheme.typography.labelSmall)
            }
            Text(String.format(Locale.US, "Confidence %.0f%%", confidence * 100))
        }
    }
}
