package com.filamentvision.input.decode

import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.input.frame.EncodedCameraFrame
import com.filamentvision.model.ByteOrderProfile
import com.filamentvision.model.ImageFormatProfile
import com.filamentvision.model.PixelFormat

class RawImageDecoder : ImageDecoder {
    override fun decode(frame: EncodedCameraFrame, format: ImageFormatProfile): ImageDecodeResult {
        if (format.width <= 0 || format.height <= 0) return ImageDecodeResult.Error("Invalid image dimensions")
        val bytesPerPixel = when (format.pixelFormat) {
            PixelFormat.GRAY8 -> 1
            PixelFormat.GRAY16 -> 2
            PixelFormat.RGB24 -> 3
            PixelFormat.RGBA32, PixelFormat.ARGB8888 -> 4
            else -> return ImageDecodeResult.Error("Pixel format ${format.pixelFormat} is not supported by the raw decoder")
        }
        val pixelStride = format.pixelStride.takeIf { it > 0 } ?: bytesPerPixel
        val rowStride = format.rowStride.takeIf { it > 0 } ?: format.width * pixelStride
        val required = (format.height - 1L) * rowStride + (format.width - 1L) * pixelStride + bytesPerPixel
        if (required > frame.payload.size) return ImageDecodeResult.Error("Payload is smaller than configured image layout")
        val pixels = IntArray(format.width * format.height)
        for (y in 0 until format.height) for (x in 0 until format.width) {
            val offset = y * rowStride + x * pixelStride
            pixels[y * format.width + x] = decodePixel(frame.payload, offset, format.pixelFormat, format.endian)
        }
        return ImageDecodeResult.Success(
            ArgbFrame(frame.cameraId, frame.timestampMillis, format.width, format.height, pixels),
        )
    }

    private fun decodePixel(bytes: ByteArray, offset: Int, format: PixelFormat, endian: ByteOrderProfile): Int = when (format) {
        PixelFormat.GRAY8 -> gray(bytes[offset].toInt() and 0xFF)
        PixelFormat.GRAY16 -> {
            val first = bytes[offset].toInt() and 0xFF
            val second = bytes[offset + 1].toInt() and 0xFF
            val sample = if (endian == ByteOrderProfile.BIG_ENDIAN) (first shl 8) or second else (second shl 8) or first
            gray(sample ushr 8)
        }
        PixelFormat.RGB24 -> argb(bytes[offset].toInt() and 0xFF, bytes[offset + 1].toInt() and 0xFF, bytes[offset + 2].toInt() and 0xFF)
        PixelFormat.RGBA32 -> argb(bytes[offset].toInt() and 0xFF, bytes[offset + 1].toInt() and 0xFF, bytes[offset + 2].toInt() and 0xFF, bytes[offset + 3].toInt() and 0xFF)
        PixelFormat.ARGB8888 -> argb(bytes[offset + 1].toInt() and 0xFF, bytes[offset + 2].toInt() and 0xFF, bytes[offset + 3].toInt() and 0xFF, bytes[offset].toInt() and 0xFF)
        else -> error("Unsupported format")
    }

    private fun gray(value: Int) = argb(value, value, value)
    private fun argb(red: Int, green: Int, blue: Int, alpha: Int = 255) =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue

}
