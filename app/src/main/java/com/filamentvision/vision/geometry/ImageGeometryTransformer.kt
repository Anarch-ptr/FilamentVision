package com.filamentvision.vision.geometry

import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.model.ImageFormatProfile

data class IntPoint(val x: Int, val y: Int)

/** Half-open integer rectangle: [left, right) x [top, bottom). */
data class IntRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

data class GeometryMapping(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val rotationDegrees: Int,
    val mirrorHorizontally: Boolean,
    val mirrorVertically: Boolean,
) {
    val outputWidth: Int = if (rotationDegrees == 90 || rotationDegrees == 270) sourceHeight else sourceWidth
    val outputHeight: Int = if (rotationDegrees == 90 || rotationDegrees == 270) sourceWidth else sourceHeight

    init {
        require(sourceWidth > 0 && sourceHeight > 0) { "Image dimensions must be positive" }
        require(rotationDegrees in SUPPORTED_ROTATIONS) { "Rotation must be 0, 90, 180, or 270 degrees" }
    }

    fun mapPoint(point: IntPoint): IntPoint {
        require(point.x in 0 until sourceWidth && point.y in 0 until sourceHeight) {
            "Point must be inside the source image"
        }
        val mirroredX = if (mirrorHorizontally) sourceWidth - 1 - point.x else point.x
        val mirroredY = if (mirrorVertically) sourceHeight - 1 - point.y else point.y
        return when (rotationDegrees) {
            0 -> IntPoint(mirroredX, mirroredY)
            90 -> IntPoint(sourceHeight - 1 - mirroredY, mirroredX)
            180 -> IntPoint(sourceWidth - 1 - mirroredX, sourceHeight - 1 - mirroredY)
            270 -> IntPoint(mirroredY, sourceWidth - 1 - mirroredX)
            else -> error("Rotation was validated during construction")
        }
    }

    fun mapRect(rect: IntRect): IntRect {
        require(rect.left in 0 until rect.right && rect.top in 0 until rect.bottom) {
            "Rectangle must be non-empty"
        }
        require(rect.right <= sourceWidth && rect.bottom <= sourceHeight) {
            "Rectangle must be inside the source image"
        }
        val corners = listOf(
            mapPoint(IntPoint(rect.left, rect.top)),
            mapPoint(IntPoint(rect.right - 1, rect.top)),
            mapPoint(IntPoint(rect.left, rect.bottom - 1)),
            mapPoint(IntPoint(rect.right - 1, rect.bottom - 1)),
        )
        return IntRect(
            left = corners.minOf { it.x },
            top = corners.minOf { it.y },
            right = corners.maxOf { it.x } + 1,
            bottom = corners.maxOf { it.y } + 1,
        )
    }

    private companion object {
        val SUPPORTED_ROTATIONS = setOf(0, 90, 180, 270)
    }
}

data class GeometryTransformResult(
    val frame: ArgbFrame,
    val mapping: GeometryMapping,
)

object ImageGeometryTransformer {
    fun transform(frame: ArgbFrame, format: ImageFormatProfile): GeometryTransformResult = transform(
        frame = frame,
        rotationDegrees = format.rotationDegrees,
        mirrorHorizontally = format.mirrorHorizontally,
        mirrorVertically = format.mirrorVertically,
    )

    fun transform(
        frame: ArgbFrame,
        rotationDegrees: Int,
        mirrorHorizontally: Boolean,
        mirrorVertically: Boolean,
    ): GeometryTransformResult {
        require(frame.pixels.size == frame.width * frame.height) { "Pixel count must match image dimensions" }
        val mapping = GeometryMapping(
            sourceWidth = frame.width,
            sourceHeight = frame.height,
            rotationDegrees = rotationDegrees,
            mirrorHorizontally = mirrorHorizontally,
            mirrorVertically = mirrorVertically,
        )
        val output = IntArray(frame.pixels.size)
        for (sourceY in 0 until frame.height) {
            for (sourceX in 0 until frame.width) {
                val destination = mapping.mapPoint(IntPoint(sourceX, sourceY))
                output[destination.y * mapping.outputWidth + destination.x] =
                    frame.pixels[sourceY * frame.width + sourceX]
            }
        }
        return GeometryTransformResult(
            frame = frame.copy(
                width = mapping.outputWidth,
                height = mapping.outputHeight,
                pixels = output,
                geometry = null,
            ),
            mapping = mapping,
        )
    }
}
