package com.example.voicenotes.ai

import java.io.File

/**
 * Enum провайдеров AI.
 */
enum class AiProvider {
    GEMINI,
    OPENAI,
    GROQ
}

/**
 * Возможности провайдера, которые используются UI и orchestration-слоем.
 */
data class AiCapabilities(
    val supportsAudio: Boolean,
    val supportsSummarize: Boolean
)

/**
 * Модель, реально доступная для введённого API ключа.
 */
data class AiModel(
    val id: String,
    val displayName: String = id
)

data class AvailableAiModels(
    val transcription: List<AiModel>,
    val summarization: List<AiModel>
)

/**
 * Входное аудио. Провайдер сам решает, как отправить файл: inline Base64 или multipart.
 */
data class AudioInput(val file: File)

/**
 * Результат текстового этапа.
 */
data class AiSummary(
    val title: String,
    val summary: String
)

/**
 * Интерфейс AI сервиса (Strategy Pattern).
 * Retrofit-детали остаются внутри реализации провайдера.
 */
interface AiService {
    val provider: AiProvider
    val capabilities: AiCapabilities

    suspend fun getAvailableModels(apiKey: String): AvailableAiModels

    suspend fun transcribe(
        audio: AudioInput,
        apiKey: String,
        modelId: String
    ): String

    suspend fun summarize(
        text: String,
        apiKey: String,
        modelId: String
    ): AiSummary
}

/**
 * Исключение при отсутствии API ключа.
 */
class MissingApiKeyException(val provider: AiProvider) :
    Exception("API key for ${provider.name} is not configured")

/**
 * Ошибка ответа AI API с кодом/сообщением для ErrorHandler.
 */
class AiApiException(
    message: String,
    val code: Int? = null,
    val provider: AiProvider? = null,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Выбранная модель или провайдер не поддерживает требуемый этап.
 */
class ProviderMisconfiguredException(message: String) : Exception(message)
