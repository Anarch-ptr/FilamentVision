package com.filamentvision.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageDisplayTransformTest {
    @Test
    fun fitTransformMapsSourceCoordinatesThroughLetterboxing() {
        val transform = ImageDisplayTransform.fit(
            sourceWidth = 200f,
            sourceHeight = 100f,
            displayWidth = 300f,
            displayHeight = 300f,
        )

        assertEquals(1.5f, transform.scale, 0.0001f)
        assertEquals(0f, transform.offsetX, 0.0001f)
        assertEquals(75f, transform.offsetY, 0.0001f)
        assertEquals(150f, transform.mapX(100f), 0.0001f)
        assertEquals(150f, transform.mapY(50f), 0.0001f)
    }
}
