package com.example.voicenotes.ai

import androidx.annotation.StringRes
import com.example.voicenotes.R

data class LocalModelFile(
    val name: String,
    val sizeBytes: Long,
    val sha256: String
)

data class LocalModelArchive(
    val name: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val rootDirectory: String
)

data class LocalModelInfo(
    val id: String,
    val version: String,
    val revision: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val speedRes: Int,
    @StringRes val qualityRes: Int,
    val minAndroidApi: Int,
    val minRamMb: Int,
    val recommendedRamMb: Int,
    val archive: LocalModelArchive,
    val files: List<LocalModelFile>,
    val huggingFaceUrl: String,
    val license: String = "Apache-2.0"
) {
    val downloadSizeBytes: Long = archive.sizeBytes
    val installedSizeBytes: Long = files.sumOf(LocalModelFile::sizeBytes)
    val sizeLabel: String = "${downloadSizeBytes / (1024 * 1024)} MB"
    val installedSizeLabel: String = "${installedSizeBytes / (1024 * 1024)} MB"
}

object LocalModelCatalog {
    val models: List<LocalModelInfo> = listOf(
        LocalModelInfo(
            id = "zipformer-ru-lite-int8",
            version = "2024-09-18",
            revision = "f766cea7df7e7204a5eac9edfba2d05e7546523a88e803f4a110b1865358d51a",
            nameRes = R.string.local_model_lite_ru_name,
            descriptionRes = R.string.local_model_lite_ru_desc,
            speedRes = R.string.local_model_speed_fastest,
            qualityRes = R.string.local_model_quality_good,
            minAndroidApi = 29,
            minRamMb = 4096,
            recommendedRamMb = 6144,
            archive = LocalModelArchive(
                name = "sherpa-onnx-small-zipformer-ru-2024-09-18.tar.bz2",
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                    "sherpa-onnx-small-zipformer-ru-2024-09-18.tar.bz2",
                sizeBytes = 109_906_654,
                sha256 = "f766cea7df7e7204a5eac9edfba2d05e7546523a88e803f4a110b1865358d51a",
                rootDirectory = "sherpa-onnx-small-zipformer-ru-2024-09-18"
            ),
            files = listOf(
                LocalModelFile(
                    name = "encoder.int8.onnx",
                    sizeBytes = 25_090_964,
                    sha256 = "75653125bf3621d86a2b140ffd84a1c3b7610fc8cd49f2b6d70cf5786be0d085"
                ),
                LocalModelFile(
                    name = "decoder.onnx",
                    sizeBytes = 2_093_080,
                    sha256 = "8537bd782f59c94b509ea501976942563e61d437373917a7736252b869e3bb2d"
                ),
                LocalModelFile(
                    name = "joiner.int8.onnx",
                    sizeBytes = 259_572,
                    sha256 = "a93e0afc2c82e94d366157b4a8b5f4cba6973ddea8f36ac94010b430be7823f1"
                ),
                LocalModelFile(
                    name = "tokens.txt",
                    sizeBytes = 6_388,
                    sha256 = "93bbbc0bae6b78c0bbb743d4aa9fded3bb5ff3aac5f0200e3a769a5a05e0fdf6"
                )
            ),
            huggingFaceUrl = "https://huggingface.co/alphacep/vosk-model-small-ru"
        ),
        LocalModelInfo(
            id = "zipformer-ru-standard-int8",
            version = "2024-09-18",
            revision = "7659a018a61d9774672720ce653a253e97e707d8bb7ccabd44a80e992d4ab121",
            nameRes = R.string.local_model_standard_ru_name,
            descriptionRes = R.string.local_model_standard_ru_desc,
            speedRes = R.string.local_model_speed_fast,
            qualityRes = R.string.local_model_quality_best,
            minAndroidApi = 29,
            minRamMb = 6144,
            recommendedRamMb = 6144,
            archive = LocalModelArchive(
                name = "sherpa-onnx-zipformer-ru-2024-09-18.tar.bz2",
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                    "sherpa-onnx-zipformer-ru-2024-09-18.tar.bz2",
                sizeBytes = 297_360_353,
                sha256 = "7659a018a61d9774672720ce653a253e97e707d8bb7ccabd44a80e992d4ab121",
                rootDirectory = "sherpa-onnx-zipformer-ru-2024-09-18"
            ),
            files = listOf(
                LocalModelFile(
                    name = "encoder.int8.onnx",
                    sizeBytes = 68_187_180,
                    sha256 = "69a347ddf7e5d39452d17f059dfedcd8c9471ca1953c320cb04ca19626149fdb"
                ),
                LocalModelFile(
                    name = "decoder.onnx",
                    sizeBytes = 2_093_079,
                    sha256 = "3977101b53640e50dff4274988ef754cd7ea539ae480baed0d18fc4966ad6917"
                ),
                LocalModelFile(
                    name = "joiner.int8.onnx",
                    sizeBytes = 259_572,
                    sha256 = "a6f550b56375326ee4a67dcac2cac027035e2dc137c08fff3557f4ffdc5cbc3e"
                ),
                LocalModelFile(
                    name = "tokens.txt",
                    sizeBytes = 6_388,
                    sha256 = "93bbbc0bae6b78c0bbb743d4aa9fded3bb5ff3aac5f0200e3a769a5a05e0fdf6"
                )
            ),
            huggingFaceUrl = "https://huggingface.co/alphacep/vosk-model-ru"
        )
    )

    const val MORE_MODELS_URL =
        "https://huggingface.co/models?pipeline_tag=automatic-speech-recognition&search=sherpa-onnx"

    fun findById(id: String?): LocalModelInfo? =
        id?.let { models.find { model -> model.id == it } }
}
