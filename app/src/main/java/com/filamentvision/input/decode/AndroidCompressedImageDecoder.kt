package com.filamentvision.input.decode

import android.graphics.BitmapFactory
import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.input.frame.EncodedCameraFrame
import com.filamentvision.model.ImageFormatProfile

/** Decodes actual JPEG/PNG payload bytes received from a transport driver. */
class AndroidCompressedImageDecoder : ImageDecoder {
    override fun decode(frame: EncodedCameraFrame, format: ImageFormatProfile): ImageDecodeResult {
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(frame.payload, 0, frame.payload.size, options)
            ?: return ImageDecodeResult.Error("The payload is not a valid compressed image")
        return try {
            if (format.width > 0 && format.height > 0 &&
                (bitmap.width != format.width || bitmap.height != format.height)
            ) {
                ImageDecodeResult.Error(
                    "Decoded image ${bitmap.width}x${bitmap.height} does not match configured ${format.width}x${format.height}",
                )
            } else {
                val source = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(source, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                ImageDecodeResult.Success(
                    ArgbFrame(
                        sourceId = frame.cameraId,
                        timestampMillis = frame.timestampMillis,
                        width = bitmap.width,
                        height = bitmap.height,
                        pixels = source,
                    ),
                )
            }
        } finally {
            bitmap.recycle()
        }
    }
}
