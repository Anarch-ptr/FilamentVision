package com.filamentvision

import androidx.compose.runtime.Composable
import com.filamentvision.navigation.AppNavigation
import com.filamentvision.ui.monitor.MonitorViewModel
import com.filamentvision.ui.theme.FilamentVisionTheme

@Composable
fun FilamentVisionApp(
    monitorViewModel: MonitorViewModel,
) {
    FilamentVisionTheme {
        AppNavigation(
            monitorViewModel = monitorViewModel,
        )
    }
}
