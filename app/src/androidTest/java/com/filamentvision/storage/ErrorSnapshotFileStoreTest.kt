package com.filamentvision.storage

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.filamentvision.camera.model.ArgbFrame
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ErrorSnapshotFileStoreTest {
    @Test
    fun savesCompactPrivateJpegWithValidDimensions(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ErrorSnapshotFileStore(context)
        val pixels = IntArray(16 * 12) { index -> if (index % 2 == 0) 0xFF101010.toInt() else 0xFFE0E0E0.toInt() }

        val saved = store.save(99L, "A", 1234L, ArgbFrame("A", 1234L, 16, 12, pixels))
        val file = File(saved.path)
        val decoded = BitmapFactory.decodeFile(saved.path)

        assertTrue(file.exists())
        assertTrue(file.length() > 0L)
        assertEquals(16, decoded.width)
        assertEquals(12, decoded.height)
        assertEquals(context.filesDir.absolutePath, file.parentFile?.parentFile?.absolutePath)
        decoded.recycle()
        file.delete()
        Unit
    }
}
