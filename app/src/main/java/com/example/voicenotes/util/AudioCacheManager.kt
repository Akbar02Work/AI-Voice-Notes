package com.example.voicenotes.util

import android.util.Log
import java.io.File

/**
 * Deletes a recording only when its note is explicitly deleted.
 *
 * Recordings live in the app's persistent files directory. They are not cache entries and must
 * not be removed by an age-based cleanup while the corresponding Room row still exists.
 */
object AudioCacheManager {
    private const val TAG = "AudioCacheManager"

    /**
     * Удаляет конкретный аудиофайл по пути.
     */
    fun deleteAudioFile(path: String) {
        try {
            val file = File(path)
            if (file.exists()) {
                if (file.delete()) {
                    Log.d(TAG, "Deleted audio file: $path")
                } else {
                    Log.w(TAG, "Failed to delete audio file: $path")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing file: $path", e)
        }
    }
}
