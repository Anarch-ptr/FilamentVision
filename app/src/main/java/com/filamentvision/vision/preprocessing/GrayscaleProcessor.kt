package com.filamentvision.vision.preprocessing

import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.model.GrayscaleMethod

class GrayscaleProcessor {
    fun process(
        input: ArgbFrame,
        method: GrayscaleMethod = GrayscaleMethod.LUMA_BT601,
        output: IntArray = IntArray(input.pixels.size),
    ): IntensityFrame {
        require(output.size >= input.pixels.size)
        input.pixels.indices.forEach { index ->
            val pixel = input.pixels[index]
            val red = pixel ushr 16 and 0xFF
            val green = pixel ushr 8 and 0xFF
            val blue = pixel and 0xFF
            output[index] = when (method) {
                GrayscaleMethod.LUMA_BT601 -> (299 * red + 587 * green + 114 * blue + 500) / 1_000
                GrayscaleMethod.AVERAGE -> (red + green + blue) / 3
            }
        }
        return IntensityFrame(input.sourceId, input.timestampMillis, input.width, input.height, output)
    }
}
