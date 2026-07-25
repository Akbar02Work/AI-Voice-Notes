package com.example.voicenotes.data

import com.example.voicenotes.ai.AiServiceRegistry
import com.example.voicenotes.ai.AiSummary
import com.example.voicenotes.ai.InferenceMode
import com.example.voicenotes.ai.LocalAiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteRepositoryLocalTest {

    @Test
    fun `local mode uses local strategy and stores synced note`() = runTest {
        val dao = mockk<NoteDao>()
        val cloudServices = mockk<AiServiceRegistry>()
        val localService = mockk<LocalAiService>()
        val preferences = mockk<UserPreferencesRepository>()
        val inserted = slot<NoteEntity>()
        val updated = slot<NoteEntity>()
        val audio = File.createTempFile("voicenotes-local-", ".m4a").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }

        coEvery { dao.insertNote(capture(inserted)) } returns 42L
        coEvery { dao.getNoteById(42L) } answers { inserted.captured }
        coEvery { dao.updateNote(capture(updated)) } returns Unit
        coEvery { preferences.getPreferences() } returns UserPreferences(
            inferenceMode = InferenceMode.LOCAL,
            selectedLocalModelId = "zipformer-ru-lite-int8"
        )
        coEvery {
            localService.transcribe(any(), "zipformer-ru-lite-int8")
        } returns "нужно проверить локальную модель"
        every {
            localService.summarize("нужно проверить локальную модель")
        } returns AiSummary(
            title = "Проверить локальную модель",
            summary = "нужно проверить локальную модель"
        )

        val repository = NoteRepository(
            noteDao = dao,
            aiServices = cloudServices,
            localAiService = localService,
            userPreferences = preferences
        )

        try {
            repository.processVoiceNote(audio)
        } finally {
            audio.delete()
        }

        coVerify {
            localService.transcribe(any(), "zipformer-ru-lite-int8")
        }
        verify(exactly = 0) { cloudServices.get(any()) }
        assertEquals(NoteStatus.SYNCED, updated.captured.status)
        assertEquals("нужно проверить локальную модель", updated.captured.rawText)
        assertEquals("Проверить локальную модель", updated.captured.title)
    }
}
