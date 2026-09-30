package com.filamentvision.camera.source

import java.util.concurrent.atomic.AtomicInteger

object CameraVisualDiagnostics {
    private val activeCounter = AtomicInteger(0)
    val activeProducerCount: Int get() = activeCounter.get()

    internal fun producerStarted() {
        activeCounter.incrementAndGet()
    }

    internal fun producerStopped() {
        activeCounter.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }
}
