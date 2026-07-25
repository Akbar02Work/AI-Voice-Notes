package com.example.voicenotes.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Запрос к Gemini API.
 */
@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>
)

@Serializable
data class GeminiModelListResponse(
    val models: List<GeminiModelInfo> = emptyList()
)

@Serializable
data class GeminiModelInfo(
    val name: String,
    val displayName: String? = null,
    val supportedGenerationMethods: List<String> = emptyList()
)

/**
 * Контент запроса/ответа (может содержать несколько частей).
 */
@Serializable
data class GeminiContent(
    val parts: List<GeminiPart> = emptyList(),
    val role: String? = null
)

/**
 * Часть контента (текст или inline данные).
 * Gemini REST expects snake_case field names.
 */
@Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("inline_data")
    val inlineData: InlineData? = null
)

/**
 * Inline данные (например, аудио в base64).
 */
@Serializable
data class InlineData(
    @SerialName("mime_type")
    val mimeType: String,
    val data: String  // base64 encoded
)

/**
 * Ответ от Gemini API.
 */
@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    @SerialName("promptFeedback")
    val promptFeedback: GeminiPromptFeedback? = null,
    val error: GeminiError? = null
)

/**
 * Кандидат ответа.
 */
@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerialName("finishReason")
    val finishReason: String? = null
)

@Serializable
data class GeminiPromptFeedback(
    @SerialName("blockReason")
    val blockReason: String? = null
)

/**
 * Ошибка от API.
 */
@Serializable
data class GeminiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)
