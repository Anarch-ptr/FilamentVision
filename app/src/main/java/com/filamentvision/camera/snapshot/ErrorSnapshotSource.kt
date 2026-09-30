package com.filamentvision.camera.snapshot

import com.filamentvision.camera.model.ArgbFrame
import java.util.concurrent.ConcurrentHashMap

interface ErrorSnapshotSource { suspend fun getLatestFrame(cameraId: String): ArgbFrame? }

class LatestCameraFrameCache : ErrorSnapshotSource {
    private val frames = ConcurrentHashMap<String, ArgbFrame>()
    fun update(frame: ArgbFrame) { frames[frame.sourceId.uppercase()] = frame.copy(pixels = frame.pixels.copyOf()) }
    override suspend fun getLatestFrame(cameraId: String): ArgbFrame? = frames[cameraId.uppercase()]?.let {
        it.copy(pixels = it.pixels.copyOf())
    }
}
