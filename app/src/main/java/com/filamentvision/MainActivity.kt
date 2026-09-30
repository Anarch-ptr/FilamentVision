package com.filamentvision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import com.filamentvision.ui.monitor.MonitorViewModel

class MainActivity : ComponentActivity() {
    private val monitorViewModel: MonitorViewModel by viewModels()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchStartedAt = SystemClock.elapsedRealtime()
        val root = FrameLayout(this)
        val startupView = buildStartupView()
        root.addView(startupView, matchParentLayoutParams())
        setContentView(root)
        startComposeAfterFirstNativeDraw(root, startupView, launchStartedAt)
    }

    private fun startComposeAfterFirstNativeDraw(
        root: FrameLayout,
        startupView: TextView,
        launchStartedAt: Long,
    ) {
        var composeScheduled = false
        startupView.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                if (composeScheduled) return
                composeScheduled = true
                Log.i(STARTUP_LOG_TAG, "native_drawable_ms=${SystemClock.elapsedRealtime() - launchStartedAt}")
                startupView.post {
                    if (startupView.viewTreeObserver.isAlive) {
                        startupView.viewTreeObserver.removeOnDrawListener(this)
                    }
                    attachComposeContent(root, startupView, launchStartedAt)
                }
            }
        })
    }

    private fun attachComposeContent(
        root: FrameLayout,
        startupView: TextView,
        launchStartedAt: Long,
    ) {
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                FilamentVisionApp(monitorViewModel = monitorViewModel)
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    post {
                        root.removeView(startupView)
                        Log.i(
                            STARTUP_LOG_TAG,
                            "compose_usable_ms=${SystemClock.elapsedRealtime() - launchStartedAt}",
                        )
                        monitorViewModel.connect()
                        requestNotificationPermissionIfNeeded()
                    }
                }
            }
        }
        root.addView(composeView, 0, matchParentLayoutParams())
    }

    private fun buildStartupView(): TextView = TextView(this).apply {
        text = getString(R.string.startup_message)
        textSize = 22f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(18, 24, 32))
    }

    private fun matchParentLayoutParams() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        const val STARTUP_LOG_TAG = "FVStartup"
    }
}
