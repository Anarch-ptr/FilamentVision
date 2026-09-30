package com.filamentvision.camera.media

import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.camera.model.FilamentVisualGeometry
import com.filamentvision.model.SimulationScenario

class FakeCameraMediaProvider(
    val width: Int = 192,
    val height: Int = 144,
) {
    var generatedPixelBufferCount: Long = 0L
        private set

    fun frame(
        cameraId: String,
        scenario: SimulationScenario,
        sequence: Long,
        timestampMillis: Long,
    ): ArgbFrame {
        val isCameraA = cameraId.equals("A", ignoreCase = true)
        val baseCenter = if (isCameraA) width * 46 / 100 else width * 54 / 100
        val motion = MOTION[(sequence % MOTION.size).toInt()]
        val center = (baseCenter + motion).coerceIn(width / 4, width * 3 / 4)
        val normalWidth = if (isCameraA) width * 28 / 100 else width * 26 / 100
        val filamentWidth = when (scenario) {
            SimulationScenario.HIGH_DIAMETER -> normalWidth + width / 14
            SimulationScenario.LOW_DIAMETER -> normalWidth - width / 14
            else -> normalWidth
        }.coerceAtLeast(4)
        val leftEdge = center - filamentWidth / 2
        val rightEdge = leftEdge + filamentWidth
        val lowContrast = scenario == SimulationScenario.LOW_CONTRAST
        val background = if (lowContrast) 92 else if (isCameraA) 38 else 46
        val filament = if (lowContrast) 116 else if (isCameraA) 190 else 174
        val pixels = IntArray(width * height)
        generatedPixelBufferCount++
        for (y in 0 until height) {
            for (x in 0 until width) {
                val texture = ((x * 13 + y * 7 + sequence.toInt() * 3) % 9) - 4
                val inside = x in leftEdge until rightEdge && y in height / 12 until height * 11 / 12
                val intensity = ((if (inside) filament else background) + texture).coerceIn(0, 255)
                val red = if (inside) (intensity + if (isCameraA) 12 else -4).coerceIn(0, 255) else intensity
                val green = intensity
                val blue = if (inside) (intensity + if (isCameraA) -10 else 10).coerceIn(0, 255) else intensity
                pixels[y * width + x] = 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
            }
        }
        return ArgbFrame(
            sourceId = cameraId.uppercase(),
            timestampMillis = timestampMillis,
            width = width,
            height = height,
            pixels = pixels,
            sequence = sequence,
            geometry = FilamentVisualGeometry(
                roiLeft = width / 8,
                roiTop = height / 10,
                roiRight = width * 7 / 8,
                roiBottom = height * 9 / 10,
                leftEdge = leftEdge,
                rightEdge = rightEdge,
                confidence = if (lowContrast) 0.72 else if (isCameraA) 0.97 else 0.95,
            ),
        )
    }

    private companion object {
        val MOTION = intArrayOf(0, 1, 2, 1, 0, -1, -2, -1)
    }
}
