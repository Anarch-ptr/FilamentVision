package com.filamentvision

import androidx.compose.runtime.Composable
import com.filamentvision.navigation.AppNavigation
import com.filamentvision.ui.monitor.MonitorViewModel
import com.filamentvision.ui.history.HistoryViewModel
import com.filamentvision.ui.trend.TrendViewModel
import com.filamentvision.ui.theme.FilamentVisionTheme

@Composable
fun FilamentVisionApp(
    monitorViewModel: MonitorViewModel,
    trendViewModel: TrendViewModel,
    historyViewModel: HistoryViewModel,
) {
    FilamentVisionTheme {
        AppNavigation(
            monitorViewModel = monitorViewModel,
            trendViewModel = trendViewModel,
            historyViewModel = historyViewModel,
        )
    }
}
