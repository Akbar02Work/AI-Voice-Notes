package com.example.voicenotes.ai

import com.example.voicenotes.network.GeminiApi
import com.example.voicenotes.network.model.GeminiContent
import com.example.voicenotes.network.model.GeminiPart
import com.example.voicenotes.network.model.GeminiRequest
import com.example.voicenotes.network.model.GeminiResponse
import com.example.voicenotes.network.model.InlineData
import android.util.Base64
import org.json.JSONObject
import javax.inject.Inject

/**
 * Реализация AiService для Google Gemini API.
 * Умеет работать с аудио файлами напрямую (multimodal).
 */
class GeminiAiService @Inject constructor(
    private val api: GeminiApi
) : AiService {
    override val provider = AiProvider.GEMINI
    override val capabilities = AiCapabilities(
        supportsAudio = true,
        supportsSummarize = true
    )

    override suspend fun getAvailableModels(apiKey: String): AvailableAiModels {
        val models = api.listModels(apiKey).models
            .asSequence()
            .filter { "generateContent" in it.supportedGenerationMethods }
            .map { info ->
                AiModel(
                    id = info.name.substringAfterLast('/'),
                    displayName = info.displayName ?: info.name.substringAfterLast('/')
                )
            }
            .filter { model ->
                val id = model.id.lowercase()
                id.startsWith("gemini-") &&
                    listOf("embedding", "image", "tts", "live").none(id::contains)
            }
            .distinctBy(AiModel::id)
            .sortedWith(
                compareByDescending<AiModel> { it.id == DEFAULT_MODEL }
                    .thenBy(AiModel::displayName)
            )
            .toList()

        if (models.isEmpty()) {
            throw AiApiException(
                message = "Gemini returned no compatible models for this API key",
                provider = provider
            )
        }
        return AvailableAiModels(
            transcription = models,
            summarization = models
        )
    }

    /**
     * Gemini принимает небольшие аудиофайлы inline в Base64.
     */
    override suspend fun transcribe(
        audio: AudioInput,
        apiKey: String,
        modelId: String
    ): String {
        val audioBase64 = Base64.encodeToString(audio.file.readBytes(), Base64.NO_WRAP)
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(
                            text = """
                                Transcribe this audio to text accurately.
                                Return ONLY the transcription, nothing else.
                                Keep the original language of the audio.
                            """.trimIndent()
                        ),
                        GeminiPart(
                            inlineData = InlineData(
                                mimeType = mimeType(audio.file),
                                data = audioBase64
                            )
                        )
                    )
                )
            )
        )

        val response = api.generateContent(modelId, apiKey, request)
        return extractText(response, operation = "transcribe audio")
    }

    override suspend fun summarize(
        text: String,
        apiKey: String,
        modelId: String
    ): AiSummary {
        val systemPrompt = """
            Ты — помощник для обработки голосовых заметок.
            Твоя задача — извлечь смысл из текста.
            
            Верни ответ СТРОГО в формате JSON БЕЗ markdown разметки:
            {"title": "Короткий заголовок (максимум 4-5 слов)", "summary": "Краткая выжимка (2-3 предложения, без буллитов и звездочек)"}
            
            ВАЖНО:
            - Не используй жирный шрифт, звездочки ** или спецсимволы
            - Не используй markdown форматирование
            - Не добавляй пояснения — только JSON
            - Язык ответа: тот же, что и входной текст
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = "$systemPrompt\n\n---\n\nТекст для обработки:\n$text")
                    )
                )
            )
        )

        val response = api.generateContent(modelId, apiKey, request)
        val jsonText = extractText(response, operation = "generate summary")
        val (title, summary) = parseAiResponse(jsonText)
        return AiSummary(title = title, summary = summary)
    }

    private fun extractText(response: GeminiResponse, operation: String): String {
        response.error?.let { error ->
            throw AiApiException(
                code = error.code,
                message = error.message ?: "Gemini error during $operation",
                provider = AiProvider.GEMINI
            )
        }

        response.promptFeedback?.blockReason?.let { reason ->
            throw AiApiException(
                message = "Request blocked by Gemini ($reason)",
                provider = AiProvider.GEMINI
            )
        }

        val candidate = response.candidates?.firstOrNull()
            ?: throw AiApiException(
                message = "Empty Gemini response while trying to $operation",
                provider = AiProvider.GEMINI
            )

        candidate.finishReason?.let { reason ->
            if (reason != "STOP" && reason != "MAX_TOKENS") {
                throw AiApiException(
                    message = "Gemini stopped with reason: $reason",
                    provider = AiProvider.GEMINI
                )
            }
        }

        return candidate.content?.parts
            ?.firstOrNull { !it.text.isNullOrBlank() }
            ?.text
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw AiApiException(
                message = "Gemini returned no text while trying to $operation",
                provider = AiProvider.GEMINI
            )
    }

    private fun parseAiResponse(jsonText: String): Pair<String, String> {
        return try {
            val cleanJson = jsonText
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val jsonObject = JSONObject(cleanJson)
            Pair(
                jsonObject.optString("title", "Voice note").ifBlank { "Voice note" },
                jsonObject.optString("summary", cleanJson).ifBlank { cleanJson.take(200) }
            )
        } catch (_: Exception) {
            Pair("Voice note", jsonText.take(200))
        }
    }

    private fun mimeType(file: java.io.File): String =
        when (file.extension.lowercase()) {
            "mp3" -> "audio/mp3"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "m4a", "mp4" -> "audio/mp4"
            "webm" -> "audio/webm"
            else -> "application/octet-stream"
        }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.6-flash"
    }
}
