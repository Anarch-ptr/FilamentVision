package com.filamentvision.vision.preprocessing

data class IntensityFrame(
    val sourceId: String,
    val timestampMillis: Long,
    val width: Int,
    val height: Int,
    val intensities: IntArray,
)
