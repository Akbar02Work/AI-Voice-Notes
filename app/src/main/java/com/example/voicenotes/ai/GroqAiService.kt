package com.example.voicenotes.ai

import com.example.voicenotes.network.OpenAiApi
import javax.inject.Inject

class GroqAiService @Inject constructor(
    api: OpenAiApi
) : OpenAiCompatibleAiService(api) {
    override val provider = AiProvider.GROQ
    override val defaultTranscriptionModel = DEFAULT_TRANSCRIPTION_MODEL
    override val defaultSummaryModel = DEFAULT_SUMMARY_MODEL

    override fun isTranscriptionModel(id: String): Boolean =
        id.lowercase().contains("whisper")

    override fun isSummaryModel(id: String): Boolean {
        val normalized = id.lowercase()
        return listOf("whisper", "guard", "tts", "audio").none(normalized::contains)
    }

    companion object {
        const val DEFAULT_TRANSCRIPTION_MODEL = "whisper-large-v3-turbo"
        const val DEFAULT_SUMMARY_MODEL = "llama-3.1-8b-instant"
    }
}
