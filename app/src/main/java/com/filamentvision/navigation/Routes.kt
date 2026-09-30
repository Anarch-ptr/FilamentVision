package com.filamentvision.navigation

sealed class TopLevelRoute(
    val route: String,
    val label: String,
    val shortLabel: String,
) {
    data object Monitor : TopLevelRoute("monitor", "Monitor", "M")
    data object Trend : TopLevelRoute("trend", "Trend", "T")
    data object History : TopLevelRoute("history", "History", "H")
    data object Device : TopLevelRoute("device", "Device", "D")
}

val topLevelRoutes = listOf(
    TopLevelRoute.Monitor,
    TopLevelRoute.Trend,
    TopLevelRoute.History,
    TopLevelRoute.Device,
)

const val DIAGNOSTICS_ROUTE = "diagnostics"
const val VISION_DIAGNOSTICS_ROUTE = "vision-diagnostics"
const val SESSION_ID_ARGUMENT = "sessionId"
const val CAMERA_ID_ARGUMENT = "cameraId"
const val SESSION_SUMMARY_ROUTE = "session-summary/{$SESSION_ID_ARGUMENT}"
const val SESSION_DETAIL_ROUTE = "session-detail/{$SESSION_ID_ARGUMENT}"
const val CAMERA_DETAIL_ROUTE = "camera/{$CAMERA_ID_ARGUMENT}"
const val CALIBRATION_PREVIEW_ROUTE = "calibration/{$CAMERA_ID_ARGUMENT}"
const val ERROR_LOG_ROUTE = "errors"
const val ERROR_ID_ARGUMENT = "errorId"
const val ERROR_DETAIL_ROUTE = "error/{$ERROR_ID_ARGUMENT}"
const val SNAPSHOT_PATH_ARGUMENT = "snapshotPath"
const val ERROR_SNAPSHOT_ROUTE = "error-snapshot/{$SNAPSHOT_PATH_ARGUMENT}"

fun sessionSummaryRoute(sessionId: String): String = "session-summary/$sessionId"
fun sessionDetailRoute(sessionId: String): String = "session-detail/$sessionId"
fun cameraDetailRoute(cameraId: String): String = "camera/$cameraId"
fun calibrationRoute(cameraId: String): String = "calibration/$cameraId"
fun errorDetailRoute(errorId: Long): String = "error/$errorId"
fun errorSnapshotRoute(filePath: String): String = "error-snapshot/${android.net.Uri.encode(filePath)}"
