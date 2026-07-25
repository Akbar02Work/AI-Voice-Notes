package com.example.voicenotes.ai

import androidx.annotation.StringRes
import com.example.voicenotes.R

/**
 * Cloud providers shown on Setup. Only [isAvailable] ones can complete setup today.
 */
data class CloudProviderOption(
    val id: String,
    val provider: AiProvider,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val apiKeyUrl: String?,
    val capabilities: AiCapabilities
)

object CloudProviderCatalog {
    val options: List<CloudProviderOption> = listOf(
        CloudProviderOption(
            id = "gemini",
            provider = AiProvider.GEMINI,
            titleRes = R.string.setup_provider_gemini,
            subtitleRes = R.string.setup_provider_gemini_subtitle,
            apiKeyUrl = "https://aistudio.google.com/app/apikey",
            capabilities = AiCapabilities(supportsAudio = true, supportsSummarize = true)
        ),
        CloudProviderOption(
            id = "openai",
            provider = AiProvider.OPENAI,
            titleRes = R.string.setup_provider_openai,
            subtitleRes = R.string.setup_provider_openai_subtitle,
            apiKeyUrl = "https://platform.openai.com/api-keys",
            capabilities = AiCapabilities(supportsAudio = true, supportsSummarize = true)
        ),
        CloudProviderOption(
            id = "groq",
            provider = AiProvider.GROQ,
            titleRes = R.string.setup_provider_groq,
            subtitleRes = R.string.setup_provider_groq_subtitle,
            apiKeyUrl = "https://console.groq.com/keys",
            capabilities = AiCapabilities(supportsAudio = true, supportsSummarize = true)
        )
    )

    fun findById(id: String): CloudProviderOption? = options.find { it.id == id }

    fun findByProvider(provider: AiProvider): CloudProviderOption? =
        options.find { it.provider == provider }
}
