package com.filamentvision.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.filamentvision.vision.runtime.PreviewLease
import com.filamentvision.vision.runtime.VisionPipelineRuntime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CameraDetailViewModel(
    private val cameraId: String,
    private val runtime: VisionPipelineRuntime,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CameraDetailUiState(cameraId.uppercase()))
    val state: StateFlow<CameraDetailUiState> = mutableState.asStateFlow()
    private var lease: PreviewLease? = null

    init {
        viewModelScope.launch {
            lease = runtime.acquirePreview("camera-detail-${cameraId.uppercase()}-${hashCode()}")
            combine(runtime.inputState, runtime.results(cameraId)) { input, result ->
                CameraDetailUiState(cameraId.uppercase(), input, result)
            }.collect { mutableState.value = it }
        }
    }

    override fun onCleared() {
        viewModelScope.launch(NonCancellable) { lease?.release() }
        super.onCleared()
    }

    companion object {
        fun factory(cameraId: String, runtime: VisionPipelineRuntime): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CameraDetailViewModel(cameraId, runtime) as T
            }
    }
}
