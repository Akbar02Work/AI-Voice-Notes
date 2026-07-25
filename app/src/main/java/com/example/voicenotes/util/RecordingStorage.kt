package com.example.voicenotes.util

import android.content.Context
import java.io.File

object RecordingStorage {
    fun directory(context: Context): File = File(context.filesDir, "recordings")
}
