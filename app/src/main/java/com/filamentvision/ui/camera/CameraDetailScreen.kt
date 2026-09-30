package com.filamentvision.ui.camera

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filamentvision.ui.monitor.MonitorViewModel
import com.filamentvision.ui.vision.VisionPipelineSection

@Composable
fun CameraDetailScreen(
    cameraId: String,
    viewModel: MonitorViewModel,
    modifier: Modifier = Modifier,
) {
    val cameraViewModel: CameraDetailViewModel = viewModel(
        key = "camera-detail-${cameraId.uppercase()}",
        factory = CameraDetailViewModel.factory(cameraId, viewModel.visionPipelineRuntime),
    )
    val state by cameraViewModel.state.collectAsStateWithLifecycle()
    VisionPipelineSection(
        cameraId = state.cameraId,
        result = state.result,
        inputState = state.inputState,
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    )
}
