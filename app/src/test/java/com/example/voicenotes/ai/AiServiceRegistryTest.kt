package com.example.voicenotes.ai

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class AiServiceRegistryTest {

    private val gemini = mockk<GeminiAiService> {
        every { provider } returns AiProvider.GEMINI
    }
    private val openAi = mockk<OpenAiService> {
        every { provider } returns AiProvider.OPENAI
    }
    private val groq = mockk<GroqAiService> {
        every { provider } returns AiProvider.GROQ
    }

    private val registry = AiServiceRegistry(gemini, openAi, groq)

    @Test
    fun `get returns Gemini service for GEMINI provider`() {
        assertEquals(gemini, registry.get(AiProvider.GEMINI))
    }

    @Test
    fun `get returns OpenAI service for OPENAI provider`() {
        assertEquals(openAi, registry.get(AiProvider.OPENAI))
    }

    @Test
    fun `get returns Groq service for GROQ provider`() {
        assertEquals(groq, registry.get(AiProvider.GROQ))
    }

    @Test
    fun `default models match provider companions`() {
        assertEquals(GeminiAiService.DEFAULT_MODEL, registry.defaultTranscriptionModel(AiProvider.GEMINI))
        assertEquals(GeminiAiService.DEFAULT_MODEL, registry.defaultSummaryModel(AiProvider.GEMINI))

        assertEquals(
            OpenAiService.DEFAULT_TRANSCRIPTION_MODEL,
            registry.defaultTranscriptionModel(AiProvider.OPENAI)
        )
        assertEquals(
            OpenAiService.DEFAULT_SUMMARY_MODEL,
            registry.defaultSummaryModel(AiProvider.OPENAI)
        )

        assertEquals(
            GroqAiService.DEFAULT_TRANSCRIPTION_MODEL,
            registry.defaultTranscriptionModel(AiProvider.GROQ)
        )
        assertEquals(
            GroqAiService.DEFAULT_SUMMARY_MODEL,
            registry.defaultSummaryModel(AiProvider.GROQ)
        )
    }
}
