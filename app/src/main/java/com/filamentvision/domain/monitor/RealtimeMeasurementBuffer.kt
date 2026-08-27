package com.filamentvision.domain.monitor

/**
 * Fixed-capacity ring buffer for the live UI path.
 *
 * Adding a sample replaces the oldest slot in-place and never grows memory.
 * A list snapshot is allocated only when a future chart explicitly requests it.
 */
class RealtimeMeasurementBuffer<T>(val capacity: Int) {
    private val values = arrayOfNulls<Any?>(capacity)
    private var nextWriteIndex = 0

    var size: Int = 0
        private set

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    fun add(value: T) {
        values[nextWriteIndex] = value
        nextWriteIndex = (nextWriteIndex + 1) % capacity
        if (size < capacity) size += 1
    }

    fun clear() {
        values.fill(null)
        nextWriteIndex = 0
        size = 0
    }

    @Suppress("UNCHECKED_CAST")
    fun snapshot(): List<T> = List(size) { offset ->
        val oldestIndex = if (size == capacity) nextWriteIndex else 0
        values[(oldestIndex + offset) % capacity] as T
    }
}
