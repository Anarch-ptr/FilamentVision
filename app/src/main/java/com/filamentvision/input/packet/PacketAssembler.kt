package com.filamentvision.input.packet

class PacketAssembler(private val maximumBytes: Int) {
    init { require(maximumBytes > 0) }
    private var buffer = ByteArray(minOf(maximumBytes, INITIAL_CAPACITY))
    private var start = 0
    private var end = 0
    val size: Int get() = end - start

    fun append(chunk: ByteArray): Boolean {
        if (chunk.isEmpty()) return true
        if (chunk.size >= maximumBytes) {
            buffer = chunk.copyOfRange(chunk.size - maximumBytes, chunk.size)
            start = 0
            end = maximumBytes
            return false
        }
        if (size + chunk.size > maximumBytes) {
            val discardCount = size + chunk.size - maximumBytes
            discard(discardCount)
            ensureCapacity(size + chunk.size)
            chunk.copyInto(buffer, end)
            end += chunk.size
            return false
        }
        ensureCapacity(size + chunk.size)
        chunk.copyInto(buffer, end)
        end += chunk.size
        return true
    }

    fun bytes(): ByteArray = buffer.copyOfRange(start, end)

    fun discard(count: Int) {
        if (count <= 0) return
        start = (start + count).coerceAtMost(end)
        if (start == end) {
            start = 0
            end = 0
        }
    }

    private fun ensureCapacity(requiredSize: Int) {
        if (buffer.size - end >= requiredSize - size) return
        if (start > 0) {
            buffer.copyInto(buffer, 0, start, end)
            end = size
            start = 0
            if (buffer.size - end >= requiredSize - size) return
        }
        var capacity = buffer.size.coerceAtLeast(1)
        while (capacity < requiredSize) capacity = minOf(maximumBytes, capacity * 2)
        buffer = buffer.copyOf(capacity)
    }

    private companion object { const val INITIAL_CAPACITY = 4_096 }
}
