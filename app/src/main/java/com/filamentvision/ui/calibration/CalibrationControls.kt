package com.filamentvision.ui.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CalibrationControls(viewModel: CalibrationPreviewViewModel, state: CalibrationPreviewUiState) {
    val calibration = state.draft.current.calibration
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Threshold ${calibration.manualThreshold}")
        Slider(
            value = calibration.manualThreshold.toFloat(),
            onValueChange = { viewModel.updateManualThreshold(it.toInt()) },
            valueRange = 0f..255f,
        )
        OutlinedTextField(
            value = calibration.mmPerPixel.toString(),
            onValueChange = { it.toDoubleOrNull()?.let(viewModel::updateMmPerPixel) },
            label = { Text("mm per pixel") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                calibration.roiLeft.toString(),
                { text -> text.toIntOrNull()?.let { value -> viewModel.updateCalibration { it.copy(roiLeft = value) } } },
                label = { Text("ROI left") }, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                calibration.roiTop.toString(),
                { text -> text.toIntOrNull()?.let { value -> viewModel.updateCalibration { it.copy(roiTop = value) } } },
                label = { Text("ROI top") }, modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                calibration.roiRight.toString(),
                { text -> text.toIntOrNull()?.let { value -> viewModel.updateCalibration { it.copy(roiRight = value) } } },
                label = { Text("ROI right") }, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                calibration.roiBottom.toString(),
                { text -> text.toIntOrNull()?.let { value -> viewModel.updateCalibration { it.copy(roiBottom = value) } } },
                label = { Text("ROI bottom") }, modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(viewModel::reset, Modifier.weight(1f)) { Text("Reset") }
            OutlinedButton(viewModel::restoreDefaults, Modifier.weight(1f)) { Text("Restore Defaults") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(viewModel::save, Modifier.weight(1f)) { Text("Save") }
            Button(viewModel::apply, Modifier.weight(1f)) { Text("Apply") }
        }
        if (state.hasUnsavedChanges) Text("UNSAVED CHANGES")
        if (state.validationErrors.isNotEmpty()) Text(state.validationErrors.values.joinToString(" · "))
    }
}
