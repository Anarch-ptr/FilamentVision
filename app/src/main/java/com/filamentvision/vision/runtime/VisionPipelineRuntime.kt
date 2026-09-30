package com.filamentvision.vision.runtime

import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.data.settings.SavedConnectionProfile
import com.filamentvision.hardware.VisionSourceDiagnostics
import com.filamentvision.input.CreatedImageInput
import com.filamentvision.input.ImageInputSource
import com.filamentvision.input.InputState
import com.filamentvision.model.CameraProfile
import com.filamentvision.model.ConnectionProfile
import com.filamentvision.model.VisionMeasurement
import com.filamentvision.model.VisionSourceState
import com.filamentvision.vision.fusion.FusionEngine
import com.filamentvision.vision.processing.VisionProcessingResult
import com.filamentvision.vision.processing.VisionProcessor
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PreviewLease internal constructor(
    private val releaseAction: suspend () -> Unit,
) {
    private val released = AtomicBoolean(false)
    suspend fun release() {
        if (released.compareAndSet(false, true)) releaseAction()
    }
}

class VisionPipelineRuntime(
    initialProfile: ConnectionProfile,
    private val sourceFactory: (ConnectionProfile) -> CreatedImageInput,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope,
    private val processor: VisionProcessor = VisionProcessor(),
    private val fusionEngine: FusionEngine = FusionEngine(),
) : AutoCloseable {
    private val lifecycleMutex = Mutex()
    private val mutableProfile = MutableStateFlow(initialProfile)
    val profile: StateFlow<ConnectionProfile> = mutableProfile.asStateFlow()
    private val mutableAppliedRevision = MutableStateFlow(0L)
    val appliedRevision: StateFlow<Long> = mutableAppliedRevision.asStateFlow()
    private var createdInput: CreatedImageInput = sourceFactory(initialProfile)
    private var collectorJob: Job? = null
    private var inputStateJob: Job? = null
    private var previewPublicationJob: Job? = null
    private val previewOwners = linkedSetOf<String>()
    private var officialOwnerActive = false
    private var totalOfficialStarts = 0
    private var emittedMeasurements = 0L
    private var firstProcessedAtNanos = 0L

    private val mutableDiagnostics = MutableStateFlow(VisionPipelineDiagnostics())
    val diagnostics: StateFlow<VisionPipelineDiagnostics> = mutableDiagnostics.asStateFlow()
    private val mutableInputState = MutableStateFlow(createdInput.source.state.value)
    val inputState: StateFlow<InputState> = mutableInputState.asStateFlow()
    private val mutableVisionState = MutableStateFlow(VisionSourceState.STOPPED)
    val visionState: StateFlow<VisionSourceState> = mutableVisionState.asStateFlow()
    private val mutableSourceDiagnostics = MutableStateFlow(VisionSourceDiagnostics())
    val sourceDiagnostics: StateFlow<VisionSourceDiagnostics> = mutableSourceDiagnostics.asStateFlow()

    private val resultFlows = mutableMapOf<String, MutableStateFlow<VisionProcessingResult?>>()
    private val latestProcessedResults = mutableMapOf<String, VisionProcessingResult>()
    private val frameFlows = mutableMapOf<String, MutableStateFlow<ArgbFrame?>>()
    private val latestMeasurements = mutableMapOf<String, com.filamentvision.vision.detection.CameraMeasurement>()
    private val mutableOfficialMeasurements = MutableSharedFlow<VisionMeasurement>(extraBufferCapacity = 16)
    val officialMeasurements: Flow<VisionMeasurement> = mutableOfficialMeasurements.asSharedFlow()

    fun results(cameraId: String): StateFlow<VisionProcessingResult?> = resultFlow(cameraId).asStateFlow()

    suspend fun acquirePreview(ownerId: String): PreviewLease {
        lifecycleMutex.withLock {
            previewOwners += ownerId
            ensureInputStartedLocked()
            publishDiagnosticsLocked()
        }
        return PreviewLease { releasePreview(ownerId) }
    }

    suspend fun startOfficialMeasurements() {
        lifecycleMutex.withLock {
            if (officialOwnerActive) return
            officialOwnerActive = true
            totalOfficialStarts++
            mutableVisionState.value = VisionSourceState.STARTING
            ensureInputStartedLocked()
            mutableVisionState.value = if (createdInput.source.activeProducerCount > 0) {
                VisionSourceState.LIVE
            } else {
                VisionSourceState.ERROR
            }
            publishDiagnosticsLocked()
        }
    }

    suspend fun stopOfficialMeasurements() {
        lifecycleMutex.withLock {
            if (!officialOwnerActive) return
            officialOwnerActive = false
            mutableVisionState.value = VisionSourceState.STOPPED
            stopInputIfUnusedLocked()
            publishDiagnosticsLocked()
        }
    }

    suspend fun applyConnectionProfile(saved: SavedConnectionProfile): Boolean = lifecycleMutex.withLock {
        if (saved.revision == mutableAppliedRevision.value && saved.profile == mutableProfile.value) return false
        val wasActive = collectorJob?.isActive == true
        if (wasActive) stopInputLocked()
        createdInput.source.close()
        createdInput = sourceFactory(saved.profile)
        mutableProfile.value = saved.profile
        mutableAppliedRevision.value = saved.revision
        mutableInputState.value = createdInput.source.state.value
        latestMeasurements.clear()
        synchronized(latestProcessedResults) { latestProcessedResults.clear() }
        resultFlows.values.forEach { it.value = null }
        frameFlows.values.forEach { it.value = null }
        if (wasActive) ensureInputStartedLocked()
        publishDiagnosticsLocked()
        true
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun draftPreview(cameraId: String, draft: StateFlow<CameraProfile>): StateFlow<VisionProcessingResult?> =
        combine(frameFlow(cameraId).filterNotNull(), draft) { frame, camera -> frame to camera }
            .mapLatest { (frame, camera) -> processor.process(frame, camera) }
            .stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0), null)

    suspend fun shutdown() {
        lifecycleMutex.withLock {
            previewOwners.clear()
            officialOwnerActive = false
            stopInputLocked()
            createdInput.source.close()
            mutableVisionState.value = VisionSourceState.STOPPED
            publishDiagnosticsLocked()
        }
    }

    override fun close() {
        scope.launch(dispatcher) { shutdown() }
    }

    private suspend fun releasePreview(ownerId: String) {
        lifecycleMutex.withLock {
            if (!previewOwners.remove(ownerId)) return
            stopInputIfUnusedLocked()
            publishDiagnosticsLocked()
        }
    }

    private suspend fun ensureInputStartedLocked() {
        if (collectorJob?.isActive == true) return
        collectorJob = scope.launch(dispatcher) {
            createdInput.source.frames.conflate().collect { processFrame(it) }
        }
        inputStateJob = scope.launch(dispatcher) {
            createdInput.source.state.collect { mutableInputState.value = it }
        }
        previewPublicationJob = scope.launch(dispatcher) {
            while (isActive) {
                delay(PREVIEW_PUBLICATION_INTERVAL_MILLIS)
                val snapshot = synchronized(latestProcessedResults) { latestProcessedResults.toMap() }
                snapshot.forEach { (cameraId, result) -> resultFlow(cameraId).value = result }
            }
        }
        createdInput.source.start()
    }

    private suspend fun stopInputIfUnusedLocked() {
        if (previewOwners.isEmpty() && !officialOwnerActive) stopInputLocked()
    }

    private suspend fun stopInputLocked() {
        createdInput.source.stop()
        collectorJob?.cancelAndJoin()
        inputStateJob?.cancelAndJoin()
        previewPublicationJob?.cancelAndJoin()
        collectorJob = null
        inputStateJob = null
        previewPublicationJob = null
        mutableInputState.value = createdInput.source.state.value
    }

    private fun processFrame(frame: ArgbFrame) {
        val camera = mutableProfile.value.cameras.firstOrNull { it.cameraId.equals(frame.sourceId, ignoreCase = true) }
            ?: return
        frameFlow(camera.cameraId).value = frame
        val result = processor.process(frame, camera)
        synchronized(latestProcessedResults) { latestProcessedResults[camera.cameraId.uppercase()] = result }
        val processed = mutableDiagnostics.value.processedFrameCount + 1
        val now = System.nanoTime()
        if (firstProcessedAtNanos == 0L) firstProcessedAtNanos = now
        val elapsedSeconds = ((now - firstProcessedAtNanos).coerceAtLeast(1L)) / 1_000_000_000.0
        mutableDiagnostics.value = mutableDiagnostics.value.copy(
            processedFrameCount = processed,
            processingFps = processed / elapsedSeconds,
        )
        if (officialOwnerActive) result.measurement?.let { publishOfficialMeasurement(camera.cameraId, it) }
    }

    private fun publishOfficialMeasurement(
        cameraId: String,
        measurement: com.filamentvision.vision.detection.CameraMeasurement,
    ) {
        latestMeasurements[cameraId.uppercase()] = measurement
        val fused = fusionEngine.fuse(
            latestMeasurements["A"],
            latestMeasurements["B"],
            mutableProfile.value.fusion,
        ) ?: return
        if (mutableOfficialMeasurements.tryEmit(fused)) emittedMeasurements++
        mutableSourceDiagnostics.value = mutableSourceDiagnostics.value.copy(
            emittedMeasurementCount = emittedMeasurements,
        )
    }

    private fun publishDiagnosticsLocked() {
        val activeInputCount = createdInput.source.activeProducerCount.coerceAtMost(1)
        mutableDiagnostics.value = mutableDiagnostics.value.copy(
            activeInputPipelineCount = activeInputCount,
            officialMeasurementProducerCount = if (officialOwnerActive && activeInputCount > 0) 1 else 0,
            previewObserverCount = previewOwners.size,
        )
        mutableSourceDiagnostics.value = VisionSourceDiagnostics(
            activeProducerCount = if (officialOwnerActive && activeInputCount > 0) 1 else 0,
            totalProducerStarts = totalOfficialStarts,
            emittedMeasurementCount = emittedMeasurements,
        )
    }

    private fun resultFlow(cameraId: String) = synchronized(resultFlows) {
        resultFlows.getOrPut(cameraId.uppercase()) { MutableStateFlow(null) }
    }

    private fun frameFlow(cameraId: String) = synchronized(frameFlows) {
        frameFlows.getOrPut(cameraId.uppercase()) { MutableStateFlow(null) }
    }

    private companion object {
        const val PREVIEW_PUBLICATION_INTERVAL_MILLIS = 125L
    }
}
