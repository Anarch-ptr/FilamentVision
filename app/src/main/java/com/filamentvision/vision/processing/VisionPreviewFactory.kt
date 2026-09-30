package com.filamentvision.vision.processing

import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.vision.preprocessing.IntensityFrame
import kotlin.math.roundToInt

class VisionPreviewFactory(private val maxLongEdge: Int = 480) {
    init {
        require(maxLongEdge > 0)
    }

    fun fromArgb(frame: ArgbFrame): PreviewFrame = resize(frame.width, frame.height, frame.pixels)

    fun fromIntensity(frame: IntensityFrame): PreviewFrame {
        val packed = IntArray(frame.intensities.size) { index ->
            val value = frame.intensities[index].coerceIn(0, 255)
            0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
        }
        return resize(frame.width, frame.height, packed)
    }

    private fun resize(width: Int, height: Int, pixels: IntArray): PreviewFrame {
        require(width > 0 && height > 0 && pixels.size >= width * height)
        val longEdge = maxOf(width, height)
        if (longEdge <= maxLongEdge) return PreviewFrame(width, height, pixels.copyOf(width * height))
        val scale = maxLongEdge.toDouble() / longEdge
        val outputWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val outputHeight = (height * scale).roundToInt().coerceAtLeast(1)
        val output = IntArray(outputWidth * outputHeight)
        for (y in 0 until outputHeight) {
            val sourceY = (y.toLong() * height / outputHeight).toInt().coerceAtMost(height - 1)
            for (x in 0 until outputWidth) {
                val sourceX = (x.toLong() * width / outputWidth).toInt().coerceAtMost(width - 1)
                output[y * outputWidth + x] = pixels[sourceY * width + sourceX]
            }
        }
        return PreviewFrame(outputWidth, outputHeight, output)
    }
}
