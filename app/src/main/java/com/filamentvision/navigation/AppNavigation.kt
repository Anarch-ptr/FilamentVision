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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.filamentvision.ui.camera.CameraDetailScreen
import com.filamentvision.ui.device.DeviceScreen
import com.filamentvision.ui.device.DiagnosticsScreen
import com.filamentvision.ui.history.HistoryScreen
import com.filamentvision.ui.history.HistoryViewModel
import com.filamentvision.ui.monitor.MonitorScreen
import com.filamentvision.ui.monitor.MonitorViewModel
import com.filamentvision.ui.session.SessionDetailScreen
import com.filamentvision.ui.session.SessionSummaryScreen
import com.filamentvision.ui.trend.TrendScreen
import com.filamentvision.ui.trend.TrendViewModel

@Composable
fun AppNavigation(
    monitorViewModel: MonitorViewModel,
    trendViewModel: TrendViewModel,
    historyViewModel: HistoryViewModel,
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
                )
            }
            composable(TopLevelRoute.History.route) {
                HistoryScreen(
                    viewModel = historyViewModel,
                    onOpenSession = { sessionId ->
                        navController.navigate(sessionDetailRoute(sessionId))
                    },
                )
            }
            composable(TopLevelRoute.Trend.route) {
                TrendScreen(viewModel = trendViewModel)
            }
            composable(TopLevelRoute.Device.route) {
                DeviceScreen(
                    viewModel = monitorViewModel,
                    onOpenDiagnostics = { navController.navigate(DIAGNOSTICS_ROUTE) },
                    onOpenCamera = { cameraId ->
                        navController.navigate(cameraDetailRoute(cameraId))
                    },
                )
            }
            composable(DIAGNOSTICS_ROUTE) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text("Back")
                    }
                    DiagnosticsScreen(
                        viewModel = monitorViewModel,
                        modifier = Modifier.weight(1f),
                    )
                }
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
