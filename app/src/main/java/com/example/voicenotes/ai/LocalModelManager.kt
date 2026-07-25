package com.example.voicenotes.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class LocalModelInstallStatus {
    NOT_INSTALLED,
    QUEUED,
    DOWNLOADING,
    VERIFYING,
    PAUSED,
    INSTALLED,
    FAILED
}

data class LocalModelState(
    val model: LocalModelInfo,
    val status: LocalModelInstallStatus,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0,
    val error: String? = null,
    val isCompatible: Boolean = true,
    val deviceRamMb: Int = 0
) {
    val isInstalled: Boolean get() = status == LocalModelInstallStatus.INSTALLED
}

@Singleton
class LocalModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)
    private val refresh = MutableStateFlow(0)
    private val deviceRamMb = detectDeviceRamMb()

    val modelStates: Flow<List<LocalModelState>> = combine(
        LocalModelCatalog.models.map(::observeModel)
    ) { states ->
        states.toList()
    }

    fun download(modelId: String) {
        val model = requireModel(modelId)
        require(isCompatible(model)) {
            "This model requires Android 10+ and at least ${model.minRamMb / 1024} GB RAM"
        }
        pausedMarker(model).delete()

        val request = OneTimeWorkRequestBuilder<LocalModelDownloadWorker>()
            .setInputData(workDataOf(LocalModelDownloadWorker.KEY_MODEL_ID to model.id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(DOWNLOAD_TAG)
            .addTag(model.id)
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(model),
            ExistingWorkPolicy.REPLACE,
            request
        )
        notifyFilesystemChanged()
    }

    /**
     * Pause is implemented as cancellation while preserving *.part files.
     * Calling [download] resumes each file with an HTTP Range request.
     */
    fun pause(modelId: String) {
        val model = requireModel(modelId)
        pausedMarker(model).apply {
            parentFile?.mkdirs()
            writeText("paused")
        }
        workManager.cancelUniqueWork(uniqueWorkName(model))
        notifyFilesystemChanged()
    }

    fun cancel(modelId: String) {
        val model = requireModel(modelId)
        workManager.cancelUniqueWork(uniqueWorkName(model))
        pausedMarker(model).delete()
        stagingDirectory(context, model).deleteRecursively()
        notifyFilesystemChanged()
    }

    fun delete(modelId: String) {
        val model = requireModel(modelId)
        cancel(modelId)
        installedDirectory(context, model).deleteRecursively()
        notifyFilesystemChanged()
    }

    fun installedModel(modelId: String?): InstalledLocalModel {
        val model = LocalModelCatalog.findById(modelId)
            ?: throw LocalModelUnavailableException("Choose an offline model in Settings")
        val directory = installedDirectory(context, model)
        if (!isInstalled(model, directory)) {
            throw LocalModelUnavailableException(
                "Download ${context.getString(model.nameRes)} before using offline mode"
            )
        }
        return InstalledLocalModel(model, directory)
    }

    private fun observeModel(model: LocalModelInfo): Flow<LocalModelState> {
        return combine(
            workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(model)),
            refresh
        ) { workInfos, _ ->
            stateFor(model, workInfos.lastOrNull())
        }
    }

    private fun stateFor(model: LocalModelInfo, workInfo: WorkInfo?): LocalModelState {
        if (isInstalled(model, installedDirectory(context, model))) {
            return LocalModelState(
                model = model,
                status = LocalModelInstallStatus.INSTALLED,
                progressPercent = 100,
                downloadedBytes = model.downloadSizeBytes,
                isCompatible = isCompatible(model),
                deviceRamMb = deviceRamMb
            )
        }
        if (pausedMarker(model).exists()) {
            return LocalModelState(
                model = model,
                status = LocalModelInstallStatus.PAUSED,
                progressPercent = workInfo?.progress?.getInt(
                    LocalModelDownloadWorker.KEY_PROGRESS,
                    partialProgress(model)
                ) ?: partialProgress(model),
                downloadedBytes = partialBytes(model),
                isCompatible = isCompatible(model),
                deviceRamMb = deviceRamMb
            )
        }

        val progress = workInfo?.progress?.getInt(LocalModelDownloadWorker.KEY_PROGRESS, 0) ?: 0
        val downloaded = workInfo?.progress
            ?.getLong(LocalModelDownloadWorker.KEY_DOWNLOADED_BYTES, 0L)
            ?: partialBytes(model)
        val stage = workInfo?.progress?.getString(LocalModelDownloadWorker.KEY_STAGE)
        val status = when (workInfo?.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> LocalModelInstallStatus.QUEUED
            WorkInfo.State.RUNNING -> {
                if (stage == LocalModelDownloadWorker.STAGE_VERIFYING) {
                    LocalModelInstallStatus.VERIFYING
                } else {
                    LocalModelInstallStatus.DOWNLOADING
                }
            }
            WorkInfo.State.FAILED -> LocalModelInstallStatus.FAILED
            else -> LocalModelInstallStatus.NOT_INSTALLED
        }
        return LocalModelState(
            model = model,
            status = status,
            progressPercent = progress,
            downloadedBytes = downloaded,
            error = workInfo?.outputData?.getString(LocalModelDownloadWorker.KEY_ERROR),
            isCompatible = isCompatible(model),
            deviceRamMb = deviceRamMb
        )
    }

    private fun isCompatible(model: LocalModelInfo): Boolean =
        Build.VERSION.SDK_INT >= model.minAndroidApi && deviceRamMb >= model.minRamMb

    private fun detectDeviceRamMb(): Int {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        val reportedMb = (info.totalMem / (1024 * 1024)).toInt()
        return ((reportedMb + 512) / 1024) * 1024
    }

    private fun partialBytes(model: LocalModelInfo): Long {
        val staging = stagingDirectory(context, model)
        val complete = File(staging, model.archive.name)
        val partial = File(staging, "${model.archive.name}.part")
        return when {
            complete.exists() -> complete.length().coerceAtMost(model.archive.sizeBytes)
            partial.exists() -> partial.length().coerceAtMost(model.archive.sizeBytes)
            else -> 0L
        }
    }

    private fun partialProgress(model: LocalModelInfo): Int =
        ((partialBytes(model) * 100) / model.downloadSizeBytes).toInt().coerceIn(0, 99)

    private fun pausedMarker(model: LocalModelInfo): File =
        File(modelRoot(context, model), ".paused")

    private fun notifyFilesystemChanged() {
        refresh.value += 1
    }

    private fun requireModel(modelId: String): LocalModelInfo =
        LocalModelCatalog.findById(modelId)
            ?: throw IllegalArgumentException("Unknown local model: $modelId")

    companion object {
        private const val DOWNLOAD_TAG = "local-model-download"

        fun uniqueWorkName(model: LocalModelInfo): String = "local-model-${model.id}"

        fun modelRoot(context: Context, model: LocalModelInfo): File =
            File(context.filesDir, "models/sherpa-onnx/${model.id}")

        fun installedDirectory(context: Context, model: LocalModelInfo): File =
            File(modelRoot(context, model), model.version)

        fun stagingDirectory(context: Context, model: LocalModelInfo): File =
            File(modelRoot(context, model), "${model.version}.staging")

        fun isInstalled(model: LocalModelInfo, directory: File): Boolean {
            if (!File(directory, "installed.marker").isFile) return false
            return model.files.all { expected ->
                File(directory, expected.name).let { it.isFile && it.length() == expected.sizeBytes }
            }
        }
    }
}

data class InstalledLocalModel(
    val info: LocalModelInfo,
    val directory: File
)

class LocalModelUnavailableException(message: String) : Exception(message)
