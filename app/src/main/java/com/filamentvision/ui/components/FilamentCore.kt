package com.filamentvision.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filamentvision.model.MeasurementStatus
import java.util.Locale

@Composable
fun FilamentCore(
    fusedDiameter: Double,
    status: MeasurementStatus,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(224.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, statusColor(status)),
        tonalElevation = 8.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = String.format(Locale.US, "%.3f", fusedDiameter),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(text = "mm", color = MaterialTheme.colorScheme.secondary)
                Text(
                    text = status.displayName(),
                    color = statusColor(status),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun statusColor(status: MeasurementStatus) = when (status) {
    MeasurementStatus.NORMAL -> MaterialTheme.colorScheme.primary
    MeasurementStatus.WARNING,
    MeasurementStatus.LOW_CONFIDENCE,
    -> Color(0xFFFFC66D)
    MeasurementStatus.OUT_OF_TOLERANCE,
    MeasurementStatus.CAMERA_MISMATCH,
    MeasurementStatus.CAMERA_A_FAILURE,
    MeasurementStatus.CAMERA_B_FAILURE,
    MeasurementStatus.DISCONNECTED,
    -> MaterialTheme.colorScheme.error
}

private fun MeasurementStatus.displayName(): String = when (this) {
    MeasurementStatus.NORMAL -> "STABLE"
    MeasurementStatus.WARNING -> "WARNING"
    MeasurementStatus.OUT_OF_TOLERANCE -> "OUT OF TOLERANCE"
    MeasurementStatus.LOW_CONFIDENCE -> "LOW CONFIDENCE"
    MeasurementStatus.CAMERA_MISMATCH -> "CAMERA MISMATCH"
    MeasurementStatus.CAMERA_A_FAILURE -> "CAMERA A FAILURE"
    MeasurementStatus.CAMERA_B_FAILURE -> "CAMERA B FAILURE"
    MeasurementStatus.DISCONNECTED -> "DISCONNECTED"
}
