package com.example.voicenotes.ai

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiServiceRegistry @Inject constructor(
    geminiAiService: GeminiAiService,
    openAiService: OpenAiService,
    groqAiService: GroqAiService
) {
    private val services = listOf(geminiAiService, openAiService, groqAiService)
        .associateBy(AiService::provider)

    fun get(provider: AiProvider): AiService =
        services[provider]
            ?: throw ProviderMisconfiguredException("Provider ${provider.name} is not configured")

    fun defaultTranscriptionModel(provider: AiProvider): String =
        when (provider) {
            AiProvider.GEMINI -> GeminiAiService.DEFAULT_MODEL
            AiProvider.OPENAI -> OpenAiService.DEFAULT_TRANSCRIPTION_MODEL
            AiProvider.GROQ -> GroqAiService.DEFAULT_TRANSCRIPTION_MODEL
        }

    fun defaultSummaryModel(provider: AiProvider): String =
        when (provider) {
            AiProvider.GEMINI -> GeminiAiService.DEFAULT_MODEL
            AiProvider.OPENAI -> OpenAiService.DEFAULT_SUMMARY_MODEL
            AiProvider.GROQ -> GroqAiService.DEFAULT_SUMMARY_MODEL
        }
}
