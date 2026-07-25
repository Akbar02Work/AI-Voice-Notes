package com.example.voicenotes.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingStorageInstrumentedTest {

    @Test
    fun recordingsUsePersistentFilesDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = RecordingStorage.directory(context)

        assertEquals(context.filesDir, directory.parentFile)
        assertEquals("recordings", directory.name)
        assertFalse(directory.absolutePath.startsWith(context.cacheDir.absolutePath))
    }
}
