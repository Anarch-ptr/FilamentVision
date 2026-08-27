package com.filamentvision.domain.monitoring

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Small lossless I/O batch buffer. It never shares storage with the realtime UI ring. */
class MeasurementWriteBuffer<T>(
    scope: CoroutineScope,
    private val batchSize: Int,
    private val flushIntervalMillis: Long,
    dispatcher: CoroutineDispatcher,
    private val writeBatch: suspend (List<T>) -> Unit,
) {
    private val bufferMutex = Mutex()
    private val pending = ArrayList<T>(batchSize)
    private val pendingCounter = AtomicInteger(0)
    private val periodicFlushJob: Job

    val pendingCount: Int get() = pendingCounter.get()

    init {
        require(batchSize > 0)
        require(flushIntervalMillis > 0L)
        periodicFlushJob = scope.launch(dispatcher) {
            while (isActive) {
                delay(flushIntervalMillis)
                flush()
            }
        }
    }

    suspend fun add(value: T) {
        bufferMutex.withLock {
            pending += value
            pendingCounter.incrementAndGet()
            if (pending.size >= batchSize) flushLocked()
        }
    }

    suspend fun flush() {
        bufferMutex.withLock { flushLocked() }
    }

    suspend fun close() {
        periodicFlushJob.cancelAndJoin()
        flush()
    }

    private suspend fun flushLocked() {
        if (pending.isEmpty()) return
        val batch = pending.toList()
        writeBatch(batch)
        pending.clear()
        pendingCounter.addAndGet(-batch.size)
    }
}
