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
const val SESSION_ID_ARGUMENT = "sessionId"
const val CAMERA_ID_ARGUMENT = "cameraId"
const val SESSION_SUMMARY_ROUTE = "session-summary/{$SESSION_ID_ARGUMENT}"
const val SESSION_DETAIL_ROUTE = "session-detail/{$SESSION_ID_ARGUMENT}"
const val CAMERA_DETAIL_ROUTE = "camera/{$CAMERA_ID_ARGUMENT}"

fun sessionSummaryRoute(sessionId: String): String = "session-summary/$sessionId"
fun sessionDetailRoute(sessionId: String): String = "session-detail/$sessionId"
fun cameraDetailRoute(cameraId: String): String = "camera/$cameraId"
