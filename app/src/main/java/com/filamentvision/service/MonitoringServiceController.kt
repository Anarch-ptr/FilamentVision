package com.filamentvision.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object MonitoringServiceController {
    fun start(context: Context, targetDiameter: Double) {
        val intent = Intent(context, MonitoringForegroundService::class.java)
            .setAction(MonitoringForegroundService.ACTION_START)
            .putExtra(MonitoringForegroundService.EXTRA_TARGET_DIAMETER, targetDiameter)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, MonitoringForegroundService::class.java)
                .setAction(MonitoringForegroundService.ACTION_STOP),
        )
    }
}
