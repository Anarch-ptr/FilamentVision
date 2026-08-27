package com.filamentvision.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filamentvision.model.ConnectionState

@Composable
fun ConnectionStatusChip(
    connectionState: ConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = connectionStatusColor(connectionState)
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = statusColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.7f)),
    ) {
        Text(
            text = connectionState.displayName(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = statusColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun connectionStatusColor(state: ConnectionState): Color = when (state) {
    ConnectionState.CONNECTED,
    -> MaterialTheme.colorScheme.primary
    ConnectionState.SEARCHING,
    ConnectionState.CONNECTING,
    ConnectionState.RECONNECTING,
    -> Color(0xFFFFC66D)
    ConnectionState.DISCONNECTED,
    ConnectionState.ERROR,
    -> MaterialTheme.colorScheme.error
}

fun ConnectionState.displayName(): String = when (this) {
    ConnectionState.DISCONNECTED -> "DISCONNECTED"
    ConnectionState.SEARCHING -> "SEARCHING"
    ConnectionState.CONNECTING -> "CONNECTING"
    ConnectionState.CONNECTED -> "CONNECTED"
    ConnectionState.RECONNECTING -> "RECONNECTING"
    ConnectionState.ERROR -> "ERROR"
}
