package com.filamentvision.ui.session

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val sessionDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", Locale.US)

fun formatSessionDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(sessionDateFormatter)

fun formatSessionDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatDiameter(value: Double): String = String.format(Locale.US, "%.3f mm", value)

fun formatConfidence(value: Double): String = String.format(Locale.US, "%.0f%%", value * 100.0)
