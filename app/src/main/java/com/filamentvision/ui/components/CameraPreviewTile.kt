package com.filamentvision.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filamentvision.model.CameraStatus
import java.util.Locale

@Composable
fun CameraPreviewTile(
    cameraName: String,
    cameraStatus: CameraStatus,
    diameter: Double,
    confidence: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(cameraName, fontWeight = FontWeight.Bold)
                Text(
                    text = cameraStatus.name.replace('_', ' '),
                    color = if (cameraStatus == CameraStatus.LIVE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Text(
                text = String.format(Locale.US, "%.3f mm", diameter),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = String.format(Locale.US, "Confidence %.0f%%", confidence * 100),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
