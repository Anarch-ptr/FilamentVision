package com.filamentvision.input

data class InputDiagnostics(
    val receivedPackets: Long = 0,
    val receivedFrames: Long = 0,
    val decodedFrames: Long = 0,
    val validDetections: Long = 0,
    val invalidFrames: Long = 0,
    val droppedFrames: Long = 0,
    val reconnectCount: Long = 0,
    val activeProducerCount: Int = 0,
)

