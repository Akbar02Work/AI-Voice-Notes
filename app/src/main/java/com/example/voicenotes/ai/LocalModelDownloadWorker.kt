package com.example.voicenotes.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.voicenotes.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.math.max

class LocalModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    private val client = OkHttpClient()
    private val archiveExtractor = LocalModelArchiveExtractor()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID)
            ?: return@withContext failure("Missing model id")
        val model = LocalModelCatalog.findById(modelId)
            ?: return@withContext failure("Unknown model: $modelId")

        try {
            setForeground(foregroundInfo(model, 0))
            val staging = LocalModelManager.stagingDirectory(applicationContext, model)
            staging.mkdirs()
            checkFreeSpace(model, staging)

            downloadArchive(model, staging)
            updateProgress(model, model.downloadSizeBytes, STAGE_VERIFYING)
            setForeground(foregroundInfo(model, 100, verifying = true))
            archiveExtractor.extract(
                archiveFile = File(staging, model.archive.name),
                outputDirectory = staging,
                rootDirectory = model.archive.rootDirectory,
                expectedFiles = model.files
            )
            model.files.forEach { file ->
                if (!isValid(File(staging, file.name), file)) {
                    throw IOException("Checksum verification failed for ${file.name}")
                }
            }
            File(staging, model.archive.name).delete()

            writeMetadata(model, staging)
            installAtomically(model, staging)
            Result.success(
                workDataOf(
                    KEY_MODEL_ID to model.id,
                    KEY_PROGRESS to 100
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure(error.message ?: "Model download failed")
        }
    }

    private suspend fun downloadArchive(
        model: LocalModelInfo,
        staging: File
    ) {
        val archive = model.archive
        val complete = File(staging, archive.name)
        if (complete.length() == archive.sizeBytes && sha256(complete) == archive.sha256) return
        complete.delete()

        val partial = File(staging, "${archive.name}.part")
        if (partial.length() > archive.sizeBytes) partial.delete()
        if (partial.length() == archive.sizeBytes) {
            if (sha256(partial) == archive.sha256) {
                moveFile(partial, complete)
                return
            }
            partial.delete()
        }

        var offset = partial.length()
        val request = Request.Builder()
            .url(archive.url)
            .header("User-Agent", "VoiceNotes/1.0")
            .apply {
                if (offset > 0) header("Range", "bytes=$offset-")
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} while downloading ${archive.name}")
            }
            val append = offset > 0 && response.code == 206
            if (offset > 0 && !append) {
                partial.delete()
                offset = 0
            }
            val body = response.body ?: throw IOException("Empty response for ${archive.name}")
            FileOutputStream(partial, append).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var lastReportedPercent = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        offset += count
                        if (offset > archive.sizeBytes) {
                            throw IOException("${archive.name} is larger than its manifest size")
                        }
                        val percent = ((offset * 100) / model.downloadSizeBytes).toInt()
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            updateProgress(model, offset, STAGE_DOWNLOADING)
                            setForeground(foregroundInfo(model, percent))
                        }
                    }
                    output.fd.sync()
                }
            }
        }

        if (partial.length() != archive.sizeBytes) {
            throw IOException(
                "Incomplete ${archive.name}: ${partial.length()} of ${archive.sizeBytes} bytes"
            )
        }
        if (sha256(partial) != archive.sha256) {
            partial.delete()
            throw IOException("Checksum verification failed for ${archive.name}")
        }
        moveFile(partial, complete)
    }

    private fun checkFreeSpace(model: LocalModelInfo, staging: File) {
        val remaining = model.downloadSizeBytes - existingBytes(model, staging)
        val safetyMargin = max(200L * 1024 * 1024, model.downloadSizeBytes * 15 / 100)
        val available = StatFs(applicationContext.filesDir.absolutePath).availableBytes
        val required = remaining + model.installedSizeBytes + safetyMargin
        if (available < required) {
            throw IOException(
                "Not enough storage. Free at least " +
                    formatMegabytes(required - available) + " more."
            )
        }
    }

    private fun existingBytes(model: LocalModelInfo, staging: File): Long {
        val complete = File(staging, model.archive.name)
        val partial = File(staging, "${model.archive.name}.part")
        return when {
            complete.length() == model.archive.sizeBytes -> model.archive.sizeBytes
            partial.exists() -> partial.length().coerceAtMost(model.archive.sizeBytes)
            else -> 0L
        }
    }

    private suspend fun updateProgress(model: LocalModelInfo, bytes: Long, stage: String) {
        val percent = ((bytes * 100) / model.downloadSizeBytes).toInt().coerceIn(0, 100)
        setProgress(
            workDataOf(
                KEY_MODEL_ID to model.id,
                KEY_PROGRESS to percent,
                KEY_DOWNLOADED_BYTES to bytes,
                KEY_STAGE to stage
            )
        )
    }

    private fun isValid(file: File, expected: LocalModelFile): Boolean =
        file.isFile && file.length() == expected.sizeBytes && sha256(file) == expected.sha256

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun writeMetadata(model: LocalModelInfo, staging: File) {
        File(staging, "LICENSE.txt").writeText(
            "Model license: ${model.license}\n" +
                "Source model: ${model.huggingFaceUrl}\n" +
                "Converted ONNX artifacts: ${model.huggingFaceUrl}\n"
        )
        val manifest = buildJsonObject {
            put("id", model.id)
            put("version", model.version)
            put("runtime", "sherpa-onnx")
            put("format", "onnx")
            put("task", "stt")
            put("languages", JsonArray(listOf(JsonPrimitive("ru"))))
            put("downloadSizeBytes", model.downloadSizeBytes)
            put("installedSizeBytes", model.installedSizeBytes)
            put("minRamMb", model.minRamMb)
            put("recommendedRamMb", model.recommendedRamMb)
            put("license", model.license)
            put("revision", model.revision)
            put("archiveSha256", model.archive.sha256)
            put(
                "files",
                JsonArray(
                    model.files.map { file ->
                        JsonObject(
                            mapOf(
                                "name" to JsonPrimitive(file.name),
                                "sizeBytes" to JsonPrimitive(file.sizeBytes),
                                "sha256" to JsonPrimitive(file.sha256)
                            )
                        )
                    }
                )
            )
        }
        File(staging, "manifest.json").writeText(manifest.toString())
        File(staging, "installed.marker").writeText(model.revision)
    }

    private fun installAtomically(model: LocalModelInfo, staging: File) {
        val destination = LocalModelManager.installedDirectory(applicationContext, model)
        if (LocalModelManager.isInstalled(model, destination)) {
            staging.deleteRecursively()
            return
        }
        val backup = File(destination.parentFile, "${destination.name}.previous")
        backup.deleteRecursively()
        if (destination.exists() && !destination.renameTo(backup)) {
            throw IOException("Unable to replace the previous model installation")
        }
        if (!staging.renameTo(destination)) {
            backup.renameTo(destination)
            throw IOException("Unable to install the verified model")
        }
        backup.deleteRecursively()
        File(destination.parentFile, ".paused").delete()
    }

    private fun moveFile(source: File, destination: File) {
        destination.delete()
        if (!source.renameTo(destination)) {
            throw IOException("Unable to install ${destination.name}")
        }
    }

    private fun foregroundInfo(
        model: LocalModelInfo,
        progress: Int,
        verifying: Boolean = false
    ): ForegroundInfo {
        createNotificationChannel()
        val cancelIntent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)
        val title = applicationContext.getString(R.string.local_download_notification_title)
        val content = if (verifying) {
            applicationContext.getString(R.string.local_download_verifying)
        } else {
            applicationContext.getString(
                R.string.local_download_notification_progress,
                applicationContext.getString(model.nameRes),
                progress.coerceIn(0, 100)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress.coerceIn(0, 100), verifying)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                applicationContext.getString(R.string.local_model_cancel),
                cancelIntent
            )
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Service.NOTIFICATION_SERVICE)
            as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.local_download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun failure(message: String): Result =
        Result.failure(workDataOf(KEY_ERROR to message.take(500)))

    private fun formatMegabytes(bytes: Long): String =
        "${(bytes + 1024 * 1024 - 1) / (1024 * 1024)} MB"

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_STAGE = "stage"
        const val KEY_ERROR = "error"
        const val STAGE_DOWNLOADING = "downloading"
        const val STAGE_VERIFYING = "verifying"

        private const val CHANNEL_ID = "offline_model_downloads"
        private const val NOTIFICATION_ID = 4307
    }
}
