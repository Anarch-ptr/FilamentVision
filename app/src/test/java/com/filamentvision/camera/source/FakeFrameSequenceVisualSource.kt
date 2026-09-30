package com.filamentvision.camera.source

import com.filamentvision.camera.media.FakeCameraMediaProvider
import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.camera.model.CameraVisualSourceState
import com.filamentvision.model.SimulationScenario
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakeFrameSequenceVisualSource(
    private val cameraId: String,
    private val mediaProvider: FakeCameraMediaProvider,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val frameIntervalMillis: Long = 80L,
    private val scenario: () -> SimulationScenario = { SimulationScenario.NORMAL },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : CameraVisualSource {
    private val lifecycleMutex = Mutex()
    private val mutableState = MutableStateFlow(CameraVisualSourceState.STOPPED)
    private val mutableFrames = MutableSharedFlow<ArgbFrame>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val producerCounter = AtomicInteger(0)
    private val emissionCounter = AtomicLong(0)
    private var producerJob: Job? = null
    private var cachedScenario: SimulationScenario? = null
    private var cachedFrames: List<ArgbFrame> = emptyList()

    override val state: StateFlow<CameraVisualSourceState> = mutableState.asStateFlow()
    override val frames: Flow<ArgbFrame> = mutableFrames
    override val activeProducerCount: Int get() = producerCounter.get()
    override val emittedFrameCount: Long get() = emissionCounter.get()

    override suspend fun start() = lifecycleMutex.withLock {
        if (producerJob?.isActive == true) return
        mutableState.value = CameraVisualSourceState.STARTING
        markProducerStarted()
        producerJob = scope.launch(dispatcher) {
            var sequence = 0L
            mutableState.value = CameraVisualSourceState.LIVE
            while (isActive) {
                val currentSequence = sequence++
                val frame = visualFrame(currentSequence, scenario(), nowMillis())
                mutableFrames.emit(frame)
                emissionCounter.incrementAndGet()
                delay(frameIntervalMillis)
            }
        }
        producerJob?.invokeOnCompletion {
            markProducerStopped()
            mutableState.value = CameraVisualSourceState.STOPPED
        }
        Unit
    }

    override suspend fun stop() = lifecycleMutex.withLock {
        producerJob?.cancelAndJoin()
        producerJob = null
        markProducerStopped()
        mutableState.value = CameraVisualSourceState.STOPPED
    }

    private fun markProducerStarted() {
        if (producerCounter.compareAndSet(0, 1)) CameraVisualDiagnostics.producerStarted()
    }

    private fun markProducerStopped() {
        if (producerCounter.compareAndSet(1, 0)) CameraVisualDiagnostics.producerStopped()
    }

    private fun visualFrame(
        sequence: Long,
        currentScenario: SimulationScenario,
        timestampMillis: Long,
    ): ArgbFrame {
        if (cachedScenario != currentScenario) {
            cachedScenario = currentScenario
            cachedFrames = List(FRAME_VARIANT_COUNT) { variant ->
                mediaProvider.frame(cameraId, currentScenario, variant.toLong(), timestampMillis = 0L)
            }
        }
        return cachedFrames[(sequence % FRAME_VARIANT_COUNT).toInt()].copy(
            timestampMillis = timestampMillis,
            sequence = sequence,
        )
    }

    private companion object {
        const val FRAME_VARIANT_COUNT = 8
    }
}
