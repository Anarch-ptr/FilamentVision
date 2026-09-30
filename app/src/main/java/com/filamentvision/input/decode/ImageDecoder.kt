package com.filamentvision.input.decode

import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.input.frame.EncodedCameraFrame
import com.filamentvision.model.ImageFormatProfile

sealed interface ImageDecodeResult {
    data class Success(val frame: ArgbFrame) : ImageDecodeResult
    data class Error(val message: String) : ImageDecodeResult
}

fun interface ImageDecoder {
    fun decode(frame: EncodedCameraFrame, format: ImageFormatProfile): ImageDecodeResult
}

