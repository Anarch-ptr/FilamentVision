package com.filamentvision.ui.vision

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filamentvision.vision.runtime.PreviewLease
import com.filamentvision.vision.runtime.VisionPipelineRuntime
import kotlinx.coroutines.launch

@Composable
fun VisionDiagnosticsScreen(
    runtime: VisionPipelineRuntime,
    onCalibrate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    DisposableEffect(runtime) {
        var lease: PreviewLease? = null
        scope.launch { lease = runtime.acquirePreview("vision-diagnostics") }
        onDispose { scope.launch { lease?.release() } }
    }
    val input by runtime.inputState.collectAsStateWithLifecycle()
    val cameraA by runtime.results("A").collectAsStateWithLifecycle()
    val cameraB by runtime.results("B").collectAsStateWithLifecycle()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button({ onCalibrate("A") }, Modifier.weight(1f)) { Text("Calibrate A") }
            Button({ onCalibrate("B") }, Modifier.weight(1f)) { Text("Calibrate B") }
        }
        VisionPipelineSection("A", cameraA, input)
        VisionPipelineSection("B", cameraB, input)
    }
}
