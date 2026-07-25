package com.example.voicenotes.ai

import com.example.voicenotes.network.OpenAiApi
import com.example.voicenotes.network.model.OpenAiMessage
import com.example.voicenotes.network.model.OpenAiRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject

/**
 * Общая реализация OpenAI-compatible transcription/chat API.
 */
abstract class OpenAiCompatibleAiService(
    private val api: OpenAiApi
) : AiService {
    override val capabilities = AiCapabilities(
        supportsAudio = true,
        supportsSummarize = true
    )

    protected abstract val defaultTranscriptionModel: String
    protected abstract val defaultSummaryModel: String

    protected abstract fun isTranscriptionModel(id: String): Boolean
    protected abstract fun isSummaryModel(id: String): Boolean

    override suspend fun getAvailableModels(apiKey: String): AvailableAiModels {
        val models = api.listModels(bearer(apiKey)).data
            .asSequence()
            .filter { it.active != false }
            .map { AiModel(id = it.id) }
            .distinctBy(AiModel::id)
            .toList()

        val transcription = models
            .filter { isTranscriptionModel(it.id) }
            .sortedWith(preferredFirst(defaultTranscriptionModel))
        val summarization = models
            .filter { isSummaryModel(it.id) }
            .sortedWith(preferredFirst(defaultSummaryModel))

        if (transcription.isEmpty() || summarization.isEmpty()) {
            throw AiApiException(
                message = "${provider.name} returned no compatible transcription or summary models",
                provider = provider
            )
        }
        return AvailableAiModels(transcription, summarization)
    }

    override suspend fun transcribe(
        audio: AudioInput,
        apiKey: String,
        modelId: String
    ): String {
        val mediaType = mimeType(audio.file.name).toMediaTypeOrNull()
        val filePart = MultipartBody.Part.createFormData(
            name = "file",
            filename = audio.file.name,
            body = audio.file.asRequestBody(mediaType)
        )
        val modelPart = modelId.toRequestBody("text/plain".toMediaType())
        return api.transcribe(bearer(apiKey), filePart, modelPart)
            .text
            .trim()
            .takeIf(String::isNotEmpty)
            ?: throw AiApiException(
                message = "${provider.name} returned an empty transcription",
                provider = provider
            )
    }

    override suspend fun summarize(
        text: String,
        apiKey: String,
        modelId: String
    ): AiSummary {
        val request = OpenAiRequest(
            model = modelId,
            messages = listOf(
                OpenAiMessage(role = "system", content = SUMMARY_PROMPT),
                OpenAiMessage(role = "user", content = "Текст для обработки:\n$text")
            )
        )
        val response = api.chatCompletion(bearer(apiKey), request)
        response.error?.let {
            throw AiApiException(
                message = it.message ?: it.code ?: "${provider.name} request failed",
                provider = provider
            )
        }
        val jsonText = response.choices
            ?.firstOrNull()
            ?.message
            ?.content
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw AiApiException(
                message = "${provider.name} returned an empty summary",
                provider = provider
            )

        return parseAiResponse(jsonText)
    }

    private fun parseAiResponse(jsonText: String): AiSummary {
        val cleanJson = jsonText
            .replace("```json", "")
            .replace("```", "")
            .trim()
        return try {
            val jsonObject = JSONObject(cleanJson)
            AiSummary(
                title = jsonObject.optString("title", "Voice note").ifBlank { "Voice note" },
                summary = jsonObject.optString("summary", cleanJson).ifBlank { cleanJson.take(200) }
            )
        } catch (_: Exception) {
            AiSummary(title = "Voice note", summary = jsonText.take(200))
        }
    }

    private fun preferredFirst(preferredId: String): Comparator<AiModel> =
        compareByDescending<AiModel> { it.id == preferredId }.thenBy(AiModel::id)

    private fun bearer(apiKey: String) = "Bearer $apiKey"

    private fun mimeType(fileName: String): String =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "m4a", "mp4" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "webm" -> "audio/webm"
            "flac" -> "audio/flac"
            else -> "application/octet-stream"
        }

    private companion object {
        val SUMMARY_PROMPT = """
            Ты — помощник для обработки голосовых заметок.
            Верни ответ строго в JSON без markdown:
            {"title":"Короткий заголовок, максимум 4-5 слов","summary":"Краткая выжимка, 2-3 предложения"}
            Не добавляй пояснений. Используй язык входного текста.
        """.trimIndent()
    }
}

class OpenAiService @Inject constructor(
    api: OpenAiApi
) : OpenAiCompatibleAiService(api) {
    override val provider = AiProvider.OPENAI
    override val defaultTranscriptionModel = DEFAULT_TRANSCRIPTION_MODEL
    override val defaultSummaryModel = DEFAULT_SUMMARY_MODEL

    override fun isTranscriptionModel(id: String): Boolean {
        val normalized = id.lowercase()
        return normalized == "whisper-1" ||
            (normalized.contains("transcribe") && !normalized.contains("realtime"))
    }

    override fun isSummaryModel(id: String): Boolean {
        val normalized = id.lowercase()
        return normalized.startsWith("gpt-") &&
            listOf("transcribe", "tts", "realtime", "audio", "image")
                .none(normalized::contains)
    }

    companion object {
        const val DEFAULT_TRANSCRIPTION_MODEL = "gpt-4o-mini-transcribe"
        const val DEFAULT_SUMMARY_MODEL = "gpt-4o-mini"
    }
}
