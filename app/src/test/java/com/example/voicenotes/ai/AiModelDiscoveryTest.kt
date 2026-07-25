package com.example.voicenotes.ai

import com.example.voicenotes.network.GeminiApi
import com.example.voicenotes.network.OpenAiApi
import com.example.voicenotes.network.model.GeminiModelInfo
import com.example.voicenotes.network.model.GeminiModelListResponse
import com.example.voicenotes.network.model.OpenAiModelInfo
import com.example.voicenotes.network.model.OpenAiModelListResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AiModelDiscoveryTest {

    @Test
    fun `Gemini discovery puts current default first and filters incompatible models`() = runTest {
        val api = mockk<GeminiApi>()
        coEvery { api.listModels("key") } returns GeminiModelListResponse(
            models = listOf(
                GeminiModelInfo(
                    name = "models/gemini-3.5-flash",
                    displayName = "Gemini 3.5 Flash",
                    supportedGenerationMethods = listOf("generateContent")
                ),
                GeminiModelInfo(
                    name = "models/text-embedding-004",
                    supportedGenerationMethods = listOf("embedContent")
                ),
                GeminiModelInfo(
                    name = "models/gemini-3.6-flash",
                    displayName = "Gemini 3.6 Flash",
                    supportedGenerationMethods = listOf("generateContent")
                )
            )
        )

        val result = GeminiAiService(api).getAvailableModels("key")

        assertEquals("gemini-3.6-flash", result.transcription.first().id)
        assertEquals(
            listOf("gemini-3.6-flash", "gemini-3.5-flash"),
            result.summarization.map(AiModel::id)
        )
    }

    @Test
    fun `OpenAI discovery separates transcription and summary models`() = runTest {
        val api = mockk<OpenAiApi>()
        coEvery { api.listModels("Bearer key") } returns OpenAiModelListResponse(
            data = listOf(
                OpenAiModelInfo("gpt-4o-mini"),
                OpenAiModelInfo("gpt-4o-mini-transcribe"),
                OpenAiModelInfo("whisper-1"),
                OpenAiModelInfo("gpt-4o-mini-tts"),
                OpenAiModelInfo("text-embedding-3-small")
            )
        )

        val result = OpenAiService(api).getAvailableModels("key")

        assertEquals(
            listOf("gpt-4o-mini-transcribe", "whisper-1"),
            result.transcription.map(AiModel::id)
        )
        assertEquals(listOf("gpt-4o-mini"), result.summarization.map(AiModel::id))
    }

    @Test
    fun `Groq discovery prefers turbo Whisper and instant Llama`() = runTest {
        val api = mockk<OpenAiApi>()
        coEvery { api.listModels("Bearer key") } returns OpenAiModelListResponse(
            data = listOf(
                OpenAiModelInfo("whisper-large-v3"),
                OpenAiModelInfo("llama-3.1-8b-instant"),
                OpenAiModelInfo("whisper-large-v3-turbo"),
                OpenAiModelInfo("llama-guard-3-8b")
            )
        )

        val result = GroqAiService(api).getAvailableModels("key")

        assertEquals("whisper-large-v3-turbo", result.transcription.first().id)
        assertEquals(listOf("llama-3.1-8b-instant"), result.summarization.map(AiModel::id))
    }
}
