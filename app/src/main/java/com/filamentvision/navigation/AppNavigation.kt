package com.filamentvision.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filamentvision.ui.camera.CameraDetailScreen
import com.filamentvision.ui.calibration.CalibrationPreviewScreen
import com.filamentvision.ui.calibration.CalibrationPreviewViewModel
import com.filamentvision.ui.device.DeviceScreen
import com.filamentvision.ui.device.DiagnosticsScreen
import com.filamentvision.ui.history.HistoryScreen
import com.filamentvision.ui.history.HistoryViewModel
import com.filamentvision.ui.error.ErrorDetailScreen
import com.filamentvision.ui.error.ErrorLogScreen
import com.filamentvision.ui.error.ErrorLogViewModel
import com.filamentvision.ui.error.ErrorSnapshotViewer
import com.filamentvision.ui.monitor.MonitorScreen
import com.filamentvision.ui.monitor.MonitorViewModel
import com.filamentvision.ui.session.SessionDetailScreen
import com.filamentvision.ui.session.SessionSummaryScreen
import com.filamentvision.ui.trend.TrendScreen
import com.filamentvision.ui.trend.TrendViewModel
import com.filamentvision.ui.vision.VisionDiagnosticsScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppNavigation(
    monitorViewModel: MonitorViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevelRoute = topLevelRoutes.any { it.route == currentRoute }

    LaunchedEffect(navController, monitorViewModel) {
        monitorViewModel.sessionFinalized.collect { sessionId ->
            navController.navigate(sessionSummaryRoute(sessionId)) {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (isTopLevelRoute) {
                NavigationBar {
                    topLevelRoutes.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(destination.shortLabel, fontWeight = FontWeight.Bold) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelRoute.Monitor.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(TopLevelRoute.Monitor.route) {
                MonitorScreen(
                    viewModel = monitorViewModel,
                    onOpenDeviceSettings = {
                        navController.navigate(TopLevelRoute.Device.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenCamera = { cameraId ->
                        navController.navigate(cameraDetailRoute(cameraId))
                    },
                    onOpenErrors = { navController.navigate(ERROR_LOG_ROUTE) },
                )
            }
            composable(TopLevelRoute.History.route) {
                val historyViewModel: HistoryViewModel = viewModel()
                HistoryScreen(
                    viewModel = historyViewModel,
                    onOpenSession = { sessionId ->
                        navController.navigate(sessionDetailRoute(sessionId))
                    },
                )
            }
            composable(TopLevelRoute.Trend.route) {
                val trendViewModel: TrendViewModel = viewModel()
                TrendScreen(viewModel = trendViewModel)
            }
            composable(TopLevelRoute.Device.route) {
                val errorViewModel: ErrorLogViewModel = viewModel()
                DeviceScreen(
                    viewModel = monitorViewModel,
                    errorViewModel = errorViewModel,
                    onOpenDiagnostics = { navController.navigate(DIAGNOSTICS_ROUTE) },
                    onOpenVisionDiagnostics = { navController.navigate(VISION_DIAGNOSTICS_ROUTE) },
                    onOpenErrors = { navController.navigate(ERROR_LOG_ROUTE) },
                    onOpenCamera = { cameraId ->
                        navController.navigate(cameraDetailRoute(cameraId))
                    },
                    onOpenCalibration = { cameraId -> navController.navigate(calibrationRoute(cameraId)) },
                )
            }
            composable(DIAGNOSTICS_ROUTE) {
                val errorViewModel: ErrorLogViewModel = viewModel()
                Column(modifier = Modifier.fillMaxSize()) {
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text("Back")
                    }
                    DiagnosticsScreen(
                        viewModel = monitorViewModel,
                        errorViewModel = errorViewModel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            composable(VISION_DIAGNOSTICS_ROUTE) {
                DetailPage(onBack = { navController.popBackStack() }) {
                    VisionDiagnosticsScreen(
                        runtime = monitorViewModel.visionPipelineRuntime,
                        onCalibrate = { navController.navigate(calibrationRoute(it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            composable(ERROR_LOG_ROUTE) {
                val errorViewModel: ErrorLogViewModel = viewModel()
                DetailPage(onBack = { navController.popBackStack() }) {
                    ErrorLogScreen(errorViewModel, { navController.navigate(errorDetailRoute(it)) }, Modifier.weight(1f))
                }
            }
            composable(ERROR_DETAIL_ROUTE, arguments = listOf(navArgument(ERROR_ID_ARGUMENT) { type = androidx.navigation.NavType.LongType })) { entry ->
                val errorViewModel: ErrorLogViewModel = viewModel()
                val errorId = entry.arguments?.getLong(ERROR_ID_ARGUMENT) ?: 0L
                DetailPage(onBack = { navController.popBackStack() }) {
                    ErrorDetailScreen(errorViewModel, errorId, { navController.navigate(errorSnapshotRoute(it)) }, Modifier.weight(1f))
                }
            }
            composable(ERROR_SNAPSHOT_ROUTE, arguments = listOf(navArgument(SNAPSHOT_PATH_ARGUMENT) {})) { entry ->
                val filePath = android.net.Uri.decode(entry.arguments?.getString(SNAPSHOT_PATH_ARGUMENT).orEmpty())
                DetailPage(onBack = { navController.popBackStack() }) { ErrorSnapshotViewer(filePath, Modifier.weight(1f)) }
            }
            composable(
                route = SESSION_SUMMARY_ROUTE,
                arguments = listOf(navArgument(SESSION_ID_ARGUMENT) {}),
            ) { entry ->
                val sessionId = entry.arguments?.getString(SESSION_ID_ARGUMENT).orEmpty()
                SessionSummaryScreen(
                    session = monitorViewModel.sessionById(sessionId),
                    onViewDetails = { navController.navigate(sessionDetailRoute(sessionId)) },
                    onDone = {
                        monitorViewModel.acknowledgeSessionSummary()
                        navController.navigate(TopLevelRoute.Monitor.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = SESSION_DETAIL_ROUTE,
                arguments = listOf(navArgument(SESSION_ID_ARGUMENT) {}),
            ) { entry ->
                val sessionId = entry.arguments?.getString(SESSION_ID_ARGUMENT).orEmpty()
                val historyViewModel: HistoryViewModel = viewModel()
                DetailPage(onBack = { navController.popBackStack() }) {
                    SessionDetailScreen(
                        sessionId = sessionId,
                        viewModel = historyViewModel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            composable(
                route = CAMERA_DETAIL_ROUTE,
                arguments = listOf(navArgument(CAMERA_ID_ARGUMENT) {}),
            ) { entry ->
                val cameraId = entry.arguments?.getString(CAMERA_ID_ARGUMENT).orEmpty()
                DetailPage(onBack = { navController.popBackStack() }) {
                    CameraDetailScreen(
                        cameraId = cameraId,
                        viewModel = monitorViewModel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            composable(
                route = CALIBRATION_PREVIEW_ROUTE,
                arguments = listOf(navArgument(CAMERA_ID_ARGUMENT) {}),
            ) { entry ->
                val cameraId = entry.arguments?.getString(CAMERA_ID_ARGUMENT).orEmpty()
                val scope = rememberCoroutineScope()
                val calibrationViewModel = remember(cameraId) {
                    CalibrationPreviewViewModel(
                        cameraId = cameraId,
                        repository = monitorViewModel.connectionSettingsRepository,
                        runtime = monitorViewModel.visionPipelineRuntime,
                        scope = scope,
                        applySavedProfile = { monitorViewModel.monitoringRuntime.applyConnectionProfile(it); Unit },
                    )
                }
                DisposableEffect(calibrationViewModel) { onDispose { calibrationViewModel.close() } }
                val inputState by monitorViewModel.visionPipelineRuntime.inputState.collectAsStateWithLifecycle()
                CalibrationPreviewScreen(
                    cameraId = cameraId,
                    viewModel = calibrationViewModel,
                    inputState = inputState,
                    onExit = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun DetailPage(
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("Back") }
        content()
    }
}
