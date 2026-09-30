package com.filamentvision.ui.calibration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filamentvision.input.InputState
import com.filamentvision.ui.vision.VisionPipelineSection

@Composable
fun CalibrationPreviewScreen(
    cameraId: String,
    viewModel: CalibrationPreviewViewModel,
    inputState: InputState,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }
    val requestBack = { if (state.hasUnsavedChanges) showExitDialog = true else onExit() }
    BackHandler(onBack = requestBack)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = requestBack) { Text("Back") }
        VisionPipelineSection(cameraId, state.result, inputState)
        CalibrationControls(viewModel, state)
    }
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Unsaved calibration changes") },
            text = { Text("Choose what to do with the current preview settings.") },
            confirmButton = {
                TextButton(onClick = { viewModel.save(); showExitDialog = false; onExit() }) { Text("Save changes") }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { viewModel.discard(); showExitDialog = false; onExit() }) { Text("Discard changes") }
                    TextButton(onClick = { showExitDialog = false }) { Text("Continue editing") }
                }
            },
        )
    }
}
