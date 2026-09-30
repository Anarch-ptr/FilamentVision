package com.filamentvision.ui.camera

import kotlin.math.min

data class ImageDisplayTransform(val scale: Float, val offsetX: Float, val offsetY: Float) {
    fun mapX(sourceX: Float): Float = offsetX + sourceX * scale
    fun mapY(sourceY: Float): Float = offsetY + sourceY * scale

    companion object {
        fun fit(
            sourceWidth: Float,
            sourceHeight: Float,
            displayWidth: Float,
            displayHeight: Float,
        ): ImageDisplayTransform {
            require(sourceWidth > 0f && sourceHeight > 0f)
            val scale = min(displayWidth / sourceWidth, displayHeight / sourceHeight)
            return ImageDisplayTransform(
                scale,
                (displayWidth - sourceWidth * scale) / 2f,
                (displayHeight - sourceHeight * scale) / 2f,
            )
        }
    }
}
