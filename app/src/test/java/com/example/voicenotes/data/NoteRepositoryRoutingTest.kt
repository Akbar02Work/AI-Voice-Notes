package com.example.voicenotes.data

import com.example.voicenotes.ai.AiProvider
import com.example.voicenotes.ai.AiService
import com.example.voicenotes.ai.AiServiceRegistry
import com.example.voicenotes.ai.AiSummary
import com.example.voicenotes.ai.InferenceMode
import com.example.voicenotes.ai.LocalAiService
import com.example.voicenotes.ai.OpenAiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteRepositoryRoutingTest {

    @Test
    fun `cloud mode routes to selected OpenAI service`() = runTest {
        val dao = mockk<NoteDao>()
        val cloudServices = mockk<AiServiceRegistry>()
        val openAi = mockk<AiService>()
        val localService = mockk<LocalAiService>(relaxed = true)
        val preferences = mockk<UserPreferencesRepository>()
        val inserted = slot<NoteEntity>()
        val updated = slot<NoteEntity>()
        val audio = File.createTempFile("voicenotes-openai-", ".m4a").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }

        coEvery { dao.insertNote(capture(inserted)) } returns 7L
        coEvery { dao.getNoteById(7L) } answers { inserted.captured }
        coEvery { dao.updateNote(capture(updated)) } returns Unit
        coEvery { preferences.getPreferences() } returns UserPreferences(
            inferenceMode = InferenceMode.CLOUD,
            selectedProvider = AiProvider.OPENAI,
            openaiApiKey = "sk-test",
            selectedTranscriptionModel = OpenAiService.DEFAULT_TRANSCRIPTION_MODEL,
            selectedSummaryModel = OpenAiService.DEFAULT_SUMMARY_MODEL
        )
        every { cloudServices.get(AiProvider.OPENAI) } returns openAi
        coEvery {
            openAi.transcribe(any(), "sk-test", OpenAiService.DEFAULT_TRANSCRIPTION_MODEL)
        } returns "hello from openai"
        coEvery {
            openAi.summarize("hello from openai", "sk-test", OpenAiService.DEFAULT_SUMMARY_MODEL)
        } returns AiSummary(title = "Hello", summary = "from openai")

        val repository = NoteRepository(dao, cloudServices, localService, preferences)

        try {
            repository.processVoiceNote(audio)
        } finally {
            audio.delete()
        }

        verify(exactly = 1) { cloudServices.get(AiProvider.OPENAI) }
        coVerify(exactly = 0) { localService.transcribe(any(), any()) }
        assertEquals(NoteStatus.SYNCED, updated.captured.status)
        assertEquals("hello from openai", updated.captured.rawText)
        assertEquals("Hello", updated.captured.title)
    }

    @Test
    fun `cloud mode uses defaults when models are unset`() = runTest {
        val dao = mockk<NoteDao>()
        val cloudServices = mockk<AiServiceRegistry>()
        val gemini = mockk<AiService>()
        val localService = mockk<LocalAiService>(relaxed = true)
        val preferences = mockk<UserPreferencesRepository>()
        val inserted = slot<NoteEntity>()
        val audio = File.createTempFile("voicenotes-gemini-", ".m4a").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }

        coEvery { dao.insertNote(capture(inserted)) } returns 3L
        coEvery { dao.getNoteById(3L) } answers { inserted.captured }
        coEvery { dao.updateNote(any()) } returns Unit
        coEvery { preferences.getPreferences() } returns UserPreferences(
            inferenceMode = InferenceMode.CLOUD,
            selectedProvider = AiProvider.GEMINI,
            geminiApiKey = "gem-key",
            selectedTranscriptionModel = null,
            selectedSummaryModel = null
        )
        every { cloudServices.get(AiProvider.GEMINI) } returns gemini
        every { cloudServices.defaultTranscriptionModel(AiProvider.GEMINI) } returns "gemini-3.6-flash"
        every { cloudServices.defaultSummaryModel(AiProvider.GEMINI) } returns "gemini-3.6-flash"
        coEvery { gemini.transcribe(any(), "gem-key", "gemini-3.6-flash") } returns "transcript"
        coEvery {
            gemini.summarize("transcript", "gem-key", "gemini-3.6-flash")
        } returns AiSummary("Title", "Summary")

        val repository = NoteRepository(dao, cloudServices, localService, preferences)

        try {
            repository.processVoiceNote(audio)
        } finally {
            audio.delete()
        }

        verify { cloudServices.defaultTranscriptionModel(AiProvider.GEMINI) }
        verify { cloudServices.defaultSummaryModel(AiProvider.GEMINI) }
        coVerify { gemini.transcribe(any(), "gem-key", "gemini-3.6-flash") }
    }

    @Test
    fun `network IOException marks note as DRAFT`() = runTest {
        val dao = mockk<NoteDao>()
        val cloudServices = mockk<AiServiceRegistry>()
        val openAi = mockk<AiService>()
        val localService = mockk<LocalAiService>(relaxed = true)
        val preferences = mockk<UserPreferencesRepository>()
        val inserted = slot<NoteEntity>()
        val updated = slot<NoteEntity>()
        val audio = File.createTempFile("voicenotes-draft-", ".m4a").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }

        coEvery { dao.insertNote(capture(inserted)) } returns 9L
        coEvery { dao.getNoteById(9L) } answers { inserted.captured }
        coEvery { dao.updateNote(capture(updated)) } returns Unit
        coEvery { preferences.getPreferences() } returns UserPreferences(
            inferenceMode = InferenceMode.CLOUD,
            selectedProvider = AiProvider.OPENAI,
            openaiApiKey = "sk-test"
        )
        every { cloudServices.get(AiProvider.OPENAI) } returns openAi
        every { cloudServices.defaultTranscriptionModel(AiProvider.OPENAI) } returns "whisper-1"
        every { cloudServices.defaultSummaryModel(AiProvider.OPENAI) } returns "gpt-4o-mini"
        coEvery { openAi.transcribe(any(), any(), any()) } throws IOException("offline")

        val repository = NoteRepository(dao, cloudServices, localService, preferences)

        try {
            try {
                repository.processVoiceNote(audio)
            } catch (_: IOException) {
                // expected
            }
        } finally {
            audio.delete()
        }

        assertEquals(NoteStatus.DRAFT, updated.captured.status)
    }

    @Test
    fun `retryDraftNotes retries cloud drafts and skips local mode`() = runTest {
        val dao = mockk<NoteDao>(relaxed = true)
        val cloudServices = mockk<AiServiceRegistry>(relaxed = true)
        val localService = mockk<LocalAiService>(relaxed = true)
        val preferences = mockk<UserPreferencesRepository>()

        coEvery { preferences.getPreferences() } returns UserPreferences(
            inferenceMode = InferenceMode.LOCAL
        )

        val repository = NoteRepository(dao, cloudServices, localService, preferences)
        repository.retryDraftNotes()

        coVerify(exactly = 0) { dao.getNotesByStatus(any()) }
    }

    @Test
    fun `retryDraftNotes processes each cloud draft`() = runTest {
        val dao = mockk<NoteDao>()
        val cloudServices = mockk<AiServiceRegistry>()
        val openAi = mockk<AiService>()
        val localService = mockk<LocalAiService>(relaxed = true)
        val preferences = mockk<UserPreferencesRepository>()
        val audio = File.createTempFile("voicenotes-retry-", ".m4a").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val draft = NoteEntity(
            id = 11L,
            title = "Draft note",
            rawText = "",
            summary = "offline",
            audioPath = audio.absolutePath,
            status = NoteStatus.DRAFT
        )
        val updated = slot<NoteEntity>()

        coEvery { preferences.getPreferences() } returns UserPreferences(
            inferenceMode = InferenceMode.CLOUD,
            selectedProvider = AiProvider.OPENAI,
            openaiApiKey = "sk-test",
            selectedTranscriptionModel = "whisper-1",
            selectedSummaryModel = "gpt-4o-mini"
        )
        coEvery { dao.getNotesByStatus(NoteStatus.DRAFT) } returns listOf(draft)
        coEvery { dao.getNoteById(11L) } returns draft andThen draft.copy(status = NoteStatus.PROCESSING)
        coEvery { dao.updateNote(capture(updated)) } returns Unit
        every { cloudServices.get(AiProvider.OPENAI) } returns openAi
        coEvery { openAi.transcribe(any(), "sk-test", "whisper-1") } returns "retried"
        coEvery {
            openAi.summarize("retried", "sk-test", "gpt-4o-mini")
        } returns AiSummary("Retried", "ok")

        val repository = NoteRepository(dao, cloudServices, localService, preferences)

        try {
            repository.retryDraftNotes()
        } finally {
            audio.delete()
        }

        assertEquals(NoteStatus.SYNCED, updated.captured.status)
        assertEquals("Retried", updated.captured.title)
    }
}
