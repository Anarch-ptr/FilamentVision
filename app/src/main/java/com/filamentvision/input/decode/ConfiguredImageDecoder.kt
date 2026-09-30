package com.filamentvision.input.decode

import com.filamentvision.input.frame.EncodedCameraFrame
import com.filamentvision.model.ImageEncoding
import com.filamentvision.model.ImageFormatProfile

class ConfiguredImageDecoder(
    private val compressedDecoder: ImageDecoder = AndroidCompressedImageDecoder(),
    private val rawDecoder: ImageDecoder = RawImageDecoder(),
) : ImageDecoder {
    override fun decode(frame: EncodedCameraFrame, format: ImageFormatProfile): ImageDecodeResult =
        when (format.encoding) {
            ImageEncoding.JPEG, ImageEncoding.PNG -> compressedDecoder.decode(frame, format)
            ImageEncoding.GRAYSCALE_RAW, ImageEncoding.RGB_RAW -> rawDecoder.decode(frame, format)
            ImageEncoding.YUV_RAW -> ImageDecodeResult.Error(
                "Configured YUV layout ${format.pixelFormat} is not supported by the installed decoder",
            )
        }
}

