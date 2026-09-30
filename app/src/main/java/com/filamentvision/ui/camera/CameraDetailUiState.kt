package com.filamentvision.ui.camera

import com.filamentvision.input.InputState
import com.filamentvision.vision.processing.VisionProcessingResult

data class CameraDetailUiState(
    val cameraId: String,
    val inputState: InputState = InputState.Unconfigured,
    val result: VisionProcessingResult? = null,
)
