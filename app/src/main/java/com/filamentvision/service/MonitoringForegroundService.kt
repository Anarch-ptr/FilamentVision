package com.filamentvision.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import com.filamentvision.FilamentVisionApplication
import com.filamentvision.MainActivity
import com.filamentvision.R
import com.filamentvision.model.MonitoringState
import com.filamentvision.domain.error.ErrorCategory
import com.filamentvision.domain.error.ErrorSeverity
import com.filamentvision.domain.error.NewErrorRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Android lifecycle and notification host. Monitoring work stays in MonitoringRuntime. */
class MonitoringForegroundService : LifecycleService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val runtime by lazy { (application as FilamentVisionApplication).monitoringRuntime }
    private var notificationJob: Job? = null
    private var terminalStateJob: Job? = null
    private var foregroundActive = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationJob = serviceScope.launch {
            while (isActive) {
                val state = runtime.state.value
                if (foregroundActive && state.monitoringState == MonitoringState.MONITORING) {
                    notificationManager().notify(
                        NOTIFICATION_ID,
                        buildNotification(state.latestMeasurement?.fusedDiameter),
                    )
                }
                delay(NOTIFICATION_REFRESH_MILLIS)
            }
        }
        terminalStateJob = serviceScope.launch {
            runtime.state.collect { state ->
                if (foregroundActive && state.monitoringState in TERMINAL_STATES) finishForegroundWork()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                foregroundActive = true
                startForeground(NOTIFICATION_ID, buildNotification(null))
                val target = intent.getDoubleExtra(EXTRA_TARGET_DIAMETER, DEFAULT_TARGET_DIAMETER)
                serviceScope.launch {
                    try {
                        runtime.startMonitoring(target)
                    } catch (failure: Throwable) {
                        (application as FilamentVisionApplication).errorRecorder.record(
                            NewErrorRecord(
                                ErrorSeverity.ERROR, ErrorCategory.SERVICE, "SERVICE_START_FAILED",
                                "Monitoring service failed to start", failure.message ?: "Foreground monitoring startup failed.",
                                "MonitoringForegroundService",
                            ),
                        )
                    }
                    if (runtime.state.value.monitoringState != MonitoringState.MONITORING) finishForegroundWork()
                }
            }
            ACTION_STOP -> serviceScope.launch {
                runtime.stopMonitoring()
                finishForegroundWork()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        terminalStateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(diameter: Double?): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MonitoringForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val content = diameter?.let { "Diameter %.3f mm · 8 Hz recording".format(it) }
            ?: "Starting 8 Hz monitoring"
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("FilamentVision monitoring")
            .setContentText(content)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    private fun createNotificationChannel() {
        notificationManager().createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Monitoring",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Continuous filament diameter recording" },
        )
    }

    private fun notificationManager(): NotificationManager = getSystemService(NotificationManager::class.java)

    private fun finishForegroundWork() {
        if (!foregroundActive) return
        foregroundActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.filamentvision.action.START_MONITORING"
        const val ACTION_STOP = "com.filamentvision.action.STOP_MONITORING"
        const val EXTRA_TARGET_DIAMETER = "targetDiameter"
        private const val CHANNEL_ID = "monitoring"
        private const val NOTIFICATION_ID = 5105
        private const val DEFAULT_TARGET_DIAMETER = 1.75
        private const val NOTIFICATION_REFRESH_MILLIS = 5_000L
        private val TERMINAL_STATES = setOf(
            MonitoringState.IDLE,
            MonitoringState.COMPLETED,
            MonitoringState.INTERRUPTED,
        )
    }
}
