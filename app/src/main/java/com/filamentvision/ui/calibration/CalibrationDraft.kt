package com.filamentvision.ui.calibration

import com.filamentvision.model.CameraProfile

data class CalibrationDraft(
    val saved: CameraProfile,
    val current: CameraProfile,
) {
    val hasUnsavedChanges: Boolean get() = current != saved
}
