package com.filamentvision.fake

import com.filamentvision.hardware.VisionSource
import com.filamentvision.hardware.VisionSourceDiagnostics
import com.filamentvision.input.InputState
import com.filamentvision.model.MeasurementStatus
import com.filamentvision.model.SimulationScenario
import com.filamentvision.model.VisionMeasurement
import com.filamentvision.model.VisionSourceState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Test-only deterministic measurement source. Never packaged in release builds.
 * Generates one smooth dual-view measurement stream for UI development.
 *
 * Start/stop is guarded by a mutex, making the single-producer guarantee atomic.
 * The flow keeps only the newest pending value so stale samples never queue.
 */
class FakeVisionSource(
    private val samplePeriodMillis: Long = DEFAULT_SAMPLE_PERIOD_MILLIS,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : VisionSource {
    private val lifecycleMutex = Mutex()
    private val sourceScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val isClosed = AtomicBoolean(false)
    private val activeProducerCounter = AtomicInteger(0)
    private val totalProducerStartCounter = AtomicInteger(0)
    private val emittedSampleCounter = AtomicLong(0)
    private val mutableState = MutableStateFlow(VisionSourceState.STOPPED)
    private val mutableInputState = MutableStateFlow<InputState>(InputState.WaitingForFrame)
    private val mutableDiagnostics = MutableStateFlow(VisionSourceDiagnostics())
    private val mutableScenario = MutableStateFlow(SimulationScenario.NORMAL)
    private val mutableMeasurements = MutableSharedFlow<VisionMeasurement>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var generationJob: Job? = null

    override val state: StateFlow<VisionSourceState> = mutableState.asStateFlow()
    override val inputState: StateFlow<InputState> = mutableInputState.asStateFlow()
    override val measurements: Flow<VisionMeasurement> = mutableMeasurements.asSharedFlow()
    override val diagnostics: StateFlow<VisionSourceDiagnostics> = mutableDiagnostics.asStateFlow()
    val scenario: StateFlow<SimulationScenario> = mutableScenario.asStateFlow()
    override val activeProducerCount: Int get() = activeProducerCounter.get()
    override val totalProducerStarts: Int get() = totalProducerStartCounter.get()
    override val emittedSampleCount: Long get() = emittedSampleCounter.get()

    override suspend fun start() = lifecycleMutex.withLock {
        check(!isClosed.get()) { "A closed FakeVisionSource cannot be restarted" }
        if (generationJob?.isActive == true) return@withLock

        mutableState.value = VisionSourceState.STARTING
        activeProducerCounter.incrementAndGet()
        totalProducerStartCounter.incrementAndGet()
        mutableDiagnostics.value = VisionSourceDiagnostics(1, totalProducerStartCounter.get(), emittedSampleCounter.get())
        generationJob = sourceScope.launch {
            var sampleIndex = 0L
            while (isActive) {
                mutableMeasurements.emit(buildMeasurement(sampleIndex, mutableScenario.value))
                emittedSampleCounter.incrementAndGet()
                mutableDiagnostics.value = VisionSourceDiagnostics(1, totalProducerStartCounter.get(), emittedSampleCounter.get())
                sampleIndex += 1
                delay(samplePeriodMillis)
            }
        }.also { job ->
            job.invokeOnCompletion { activeProducerCounter.decrementAndGet() }
        }
        mutableState.value = VisionSourceState.LIVE
        mutableInputState.value = InputState.Streaming
    }

    override suspend fun stop() = lifecycleMutex.withLock {
        generationJob?.cancelAndJoin()
        generationJob = null
        mutableState.value = VisionSourceState.STOPPED
        mutableInputState.value = InputState.WaitingForFrame
        mutableDiagnostics.value = VisionSourceDiagnostics(0, totalProducerStartCounter.get(), emittedSampleCounter.get())
    }

    fun selectScenario(scenario: SimulationScenario) {
        mutableScenario.value = scenario
    }

    override fun close() {
        if (!isClosed.compareAndSet(false, true)) return
        generationJob = null
        sourceScope.cancel()
        mutableState.value = VisionSourceState.STOPPED
    }

    // All simulated sensor behavior stays below this boundary; UI only renders outputs.
    private fun buildMeasurement(
        sampleIndex: Long,
        scenario: SimulationScenario,
    ): VisionMeasurement {
        val drift = sin(sampleIndex * 0.16) * 0.0045
        val viewVariation = sin(sampleIndex * 0.31) * 0.0018
        val scenarioOffset = when (scenario) {
            SimulationScenario.HIGH_DIAMETER -> 0.034
            SimulationScenario.LOW_DIAMETER -> -0.034
            else -> 0.0
        }
        val diameterA = TARGET_DIAMETER_MM + drift + viewVariation + scenarioOffset
        val diameterB = TARGET_DIAMETER_MM + drift - viewVariation + scenarioOffset +
            if (scenario == SimulationScenario.CAMERA_MISMATCH) 0.028 else 0.0015
        val confidenceA = when (scenario) {
            SimulationScenario.LOW_CONFIDENCE -> 0.58
            SimulationScenario.CAMERA_A_FAILURE -> 0.0
            else -> 0.98
        }
        val confidenceB = when (scenario) {
            SimulationScenario.LOW_CONFIDENCE -> 0.62
            SimulationScenario.CAMERA_B_FAILURE -> 0.0
            SimulationScenario.CAMERA_MISMATCH -> 0.82
            else -> 0.97
        }
        val fusedDiameter = weightedDiameter(
            diameterA = diameterA,
            diameterB = diameterB,
            confidenceA = confidenceA,
            confidenceB = confidenceB,
        )
        val difference = abs(diameterA - diameterB)
        val overallConfidence = (confidenceA + confidenceB) / 2.0

        return VisionMeasurement(
            diameterA = diameterA,
            diameterB = diameterB,
            fusedDiameter = fusedDiameter,
            shapeDifference = difference,
            cameraAConfidence = confidenceA,
            cameraBConfidence = confidenceB,
            confidence = overallConfidence,
            status = evaluateMeasurementStatus(
                scenario = scenario,
                fusedDiameter = fusedDiameter,
                shapeDifference = difference,
                confidence = overallConfidence,
            ),
            timestamp = nowMillis(),
        )
    }

    private fun weightedDiameter(
        diameterA: Double,
        diameterB: Double,
        confidenceA: Double,
        confidenceB: Double,
    ): Double {
        val totalConfidence = confidenceA + confidenceB
        return if (totalConfidence > 0.0) {
            (diameterA * confidenceA + diameterB * confidenceB) / totalConfidence
        } else {
            (diameterA + diameterB) / 2.0
        }
    }

    private fun evaluateMeasurementStatus(
        scenario: SimulationScenario,
        fusedDiameter: Double,
        shapeDifference: Double,
        confidence: Double,
    ): MeasurementStatus = when {
        scenario == SimulationScenario.CAMERA_A_FAILURE -> MeasurementStatus.CAMERA_A_FAILURE
        scenario == SimulationScenario.CAMERA_B_FAILURE -> MeasurementStatus.CAMERA_B_FAILURE
        confidence < 0.70 -> MeasurementStatus.LOW_CONFIDENCE
        shapeDifference > 0.020 -> MeasurementStatus.CAMERA_MISMATCH
        abs(fusedDiameter - TARGET_DIAMETER_MM) > 0.025 -> MeasurementStatus.OUT_OF_TOLERANCE
        abs(fusedDiameter - TARGET_DIAMETER_MM) > 0.012 -> MeasurementStatus.WARNING
        else -> MeasurementStatus.NORMAL
    }

    private companion object {
        const val TARGET_DIAMETER_MM = 1.750
        const val DEFAULT_SAMPLE_PERIOD_MILLIS = 125L
    }
}
