package com.filamentvision.vision.preprocessing

import kotlin.math.abs
import kotlin.math.max

class EdgeMapProcessor {
    fun process(input: IntensityFrame, output: IntArray = IntArray(input.intensities.size)): IntensityFrame {
        require(output.size >= input.intensities.size)
        output.fill(0, 0, input.intensities.size)
        for (y in 0 until input.height) {
            val rowStart = y * input.width
            for (x in 1 until input.width - 1) {
                val index = rowStart + x
                val left = abs(input.intensities[index] - input.intensities[index - 1])
                val right = abs(input.intensities[index + 1] - input.intensities[index])
                output[index] = max(left, right).coerceAtMost(255)
            }
        }
        return input.copy(intensities = output)
    }

    fun process(
        grayscale: IntensityFrame,
        threshold: IntensityFrame,
        edgeSensitivity: Int,
        output: IntArray = IntArray(grayscale.intensities.size),
    ): IntensityFrame {
        require(grayscale.width == threshold.width && grayscale.height == threshold.height)
        require(output.size >= grayscale.intensities.size)
        val sensitivity = edgeSensitivity.coerceIn(0, 255)
        output.fill(0, 0, grayscale.intensities.size)
        for (y in 0 until grayscale.height) {
            val rowStart = y * grayscale.width
            for (x in 0 until grayscale.width) {
                val index = rowStart + x
                val leftEdge = x > 0 &&
                    threshold.intensities[index] != threshold.intensities[index - 1] &&
                    abs(grayscale.intensities[index] - grayscale.intensities[index - 1]) >= sensitivity
                val rightEdge = x + 1 < grayscale.width &&
                    threshold.intensities[index] != threshold.intensities[index + 1] &&
                    abs(grayscale.intensities[index] - grayscale.intensities[index + 1]) >= sensitivity
                output[index] = if (leftEdge || rightEdge) 255 else 0
            }
        }
        return grayscale.copy(intensities = output)
    }
}
