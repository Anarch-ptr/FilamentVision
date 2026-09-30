package com.filamentvision.model

enum class SimulationScenario(val displayName: String) {
    NORMAL("Normal"),
    HIGH_DIAMETER("High Diameter"),
    LOW_DIAMETER("Low Diameter"),
    LOW_CONTRAST("Low Contrast"),
    CAMERA_MISMATCH("Camera Mismatch"),
    LOW_CONFIDENCE("Low Confidence"),
    CAMERA_A_FAILURE("Camera A Failure"),
    CAMERA_B_FAILURE("Camera B Failure"),
    CONNECTION_LOST("Connection Lost"),
}
