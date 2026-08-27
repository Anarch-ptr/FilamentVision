package com.filamentvision

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.filamentvision.ui.monitor.MonitorViewModel
import com.filamentvision.ui.history.HistoryViewModel
import com.filamentvision.ui.trend.TrendViewModel

class MainActivity : ComponentActivity() {
    private val monitorViewModel: MonitorViewModel by viewModels()
    private val trendViewModel: TrendViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            FilamentVisionApp(
                monitorViewModel = monitorViewModel,
                trendViewModel = trendViewModel,
                historyViewModel = historyViewModel,
            )
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
