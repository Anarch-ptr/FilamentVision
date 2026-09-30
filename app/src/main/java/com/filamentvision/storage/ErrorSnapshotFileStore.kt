package com.filamentvision.storage

import android.content.Context
import android.graphics.Bitmap
import com.filamentvision.camera.model.ArgbFrame
import com.filamentvision.domain.error.ErrorSnapshotPolicy
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SavedSnapshotFile(val path: String, val width: Int, val height: Int, val sizeBytes: Long, val mimeType: String)

class ErrorSnapshotFileStore(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val directory = File(context.filesDir, "error_snapshots")

    suspend fun save(errorId: Long, cameraId: String, timestamp: Long, frame: ArgbFrame): SavedSnapshotFile =
        withContext(dispatcher) {
            directory.mkdirs()
            val file = File(directory, "error_${errorId}_${timestamp}_camera_${cameraId.lowercase()}.jpg")
            val bitmap = Bitmap.createBitmap(frame.pixels, frame.width, frame.height, Bitmap.Config.ARGB_8888)
            try {
                FileOutputStream(file).use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, ErrorSnapshotPolicy.JPEG_QUALITY, output))
                }
            } finally {
                bitmap.recycle()
            }
            SavedSnapshotFile(file.absolutePath, frame.width, frame.height, file.length(), "image/jpeg")
        }

    suspend fun delete(path: String): Boolean = withContext(dispatcher) {
        val file = File(path)
        !file.exists() || file.delete()
    }
}
