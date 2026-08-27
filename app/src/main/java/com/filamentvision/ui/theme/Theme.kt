package com.filamentvision.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val IndustrialColorScheme = darkColorScheme(
    primary = Color(0xFF59D4C6),
    onPrimary = Color(0xFF00201D),
    secondary = Color(0xFF93AFC8),
    background = Color(0xFF0B1117),
    surface = Color(0xFF111B24),
    surfaceVariant = Color(0xFF1B2A36),
    onBackground = Color(0xFFE4EDF3),
    onSurface = Color(0xFFE4EDF3),
    error = Color(0xFFFFB4AB),
)

@Composable
fun FilamentVisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IndustrialColorScheme,
        content = content,
    )
}

