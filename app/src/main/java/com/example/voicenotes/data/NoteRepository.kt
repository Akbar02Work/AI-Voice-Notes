package com.example.voicenotes.data

import com.example.voicenotes.ai.AiServiceRegistry
import com.example.voicenotes.ai.AiSummary
import com.example.voicenotes.ai.AudioInput
import com.example.voicenotes.ai.InferenceMode
import com.example.voicenotes.ai.LocalAiService
import com.example.voicenotes.ai.MissingApiKeyException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий для работы с голосовыми заметками.
 * Инкапсулирует логику взаимодействия с AI сервисами и Room базой данных.
 */
@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val aiServices: AiServiceRegistry,
    private val localAiService: LocalAiService,
    private val userPreferences: UserPreferencesRepository
) {
    private val draftRetryMutex = Mutex()

    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()

    fun observeNoteById(noteId: Long): Flow<NoteEntity?> = noteDao.observeNoteById(noteId)

    suspend fun getNoteById(noteId: Long): NoteEntity? = noteDao.getNoteById(noteId)

    suspend fun deleteNote(noteId: Long) = noteDao.deleteNote(noteId)

    suspend fun updateNoteTitle(noteId: Long, newTitle: String) =
        noteDao.updateNoteTitle(noteId, newTitle)

    suspend fun setNotePinned(noteId: Long, isPinned: Boolean) =
        noteDao.updatePinned(noteId, isPinned)

    /**
     * Создаёт PROCESSING-запись и запускает AI-обработку.
     * При ошибке статус становится DRAFT (сеть) или FAILED (остальное),
     * заголовок больше не остаётся "Processing...".
     */
    suspend fun processVoiceNote(audioFile: File) {
        val tempNote = NoteEntity(
            title = "Processing...",
            rawText = "",
            summary = "",
            audioPath = audioFile.absolutePath,
            status = NoteStatus.PROCESSING
        )
        val noteId = noteDao.insertNote(tempNote)
        processNoteById(noteId, audioFile)
    }

    suspend fun retryNote(noteId: Long) {
        val note = noteDao.getNoteById(noteId) ?: return
        val audioFile = File(note.audioPath)

        if (!audioFile.exists()) {
            markFailed(note, "Audio file missing")
            throw IOException("Audio file not found: ${note.audioPath}")
        }

        noteDao.updateNote(
            note.copy(
                title = "Processing...",
                status = NoteStatus.PROCESSING,
                summary = ""
            )
        )
        processNoteById(noteId, audioFile)
    }

    /**
     * Re-processes DRAFT notes when connectivity returns (cloud mode only).
     * Failures are absorbed per-note so one bad draft does not block the rest.
     */
    suspend fun retryDraftNotes() = draftRetryMutex.withLock {
        val prefs = userPreferences.getPreferences()
        if (prefs.inferenceMode != InferenceMode.CLOUD) return@withLock

        val drafts = noteDao.getNotesByStatus(NoteStatus.DRAFT)
        for (draft in drafts) {
            try {
                retryNote(draft.id)
            } catch (_: Exception) {
                // Status already updated inside processNoteById / retryNote.
            }
        }
    }

    private suspend fun processNoteById(noteId: Long, audioFile: File) {
        val currentNote = noteDao.getNoteById(noteId) ?: return

        try {
            val prefs = userPreferences.getPreferences()

            if (!audioFile.exists() || audioFile.length() == 0L) {
                throw IOException("Audio file is missing or empty")
            }

            val (transcription, summary) = when (prefs.inferenceMode) {
                InferenceMode.LOCAL -> {
                    val text = localAiService.transcribe(
                        audio = AudioInput(audioFile),
                        modelId = prefs.selectedLocalModelId
                    )
                    text to localAiService.summarize(text)
                }
                InferenceMode.CLOUD -> processWithCloud(audioFile, prefs)
            }

            noteDao.updateNote(
                currentNote.copy(
                    title = summary.title,
                    rawText = transcription,
                    summary = summary.summary,
                    status = NoteStatus.SYNCED
                )
            )
        } catch (e: IOException) {
            markDraft(currentNote, e.message)
            throw e
        } catch (e: Exception) {
            markFailed(currentNote, e.message)
            throw e
        }
    }

    private suspend fun processWithCloud(
        audioFile: File,
        prefs: UserPreferences
    ): Pair<String, AiSummary> {
        val apiKey = prefs.apiKeyFor(prefs.selectedProvider)
        if (apiKey.isBlank()) {
            throw MissingApiKeyException(prefs.selectedProvider)
        }
        val service = aiServices.get(prefs.selectedProvider)
        val transcriptionModel = prefs.selectedTranscriptionModel
            ?: aiServices.defaultTranscriptionModel(prefs.selectedProvider)
        val summaryModel = prefs.selectedSummaryModel
            ?: aiServices.defaultSummaryModel(prefs.selectedProvider)
        val transcription = service.transcribe(
            audio = AudioInput(audioFile),
            apiKey = apiKey,
            modelId = transcriptionModel
        )
        return transcription to service.summarize(
            text = transcription,
            apiKey = apiKey,
            modelId = summaryModel
        )
    }

    private suspend fun markDraft(note: NoteEntity, detail: String?) {
        noteDao.updateNote(
            note.copy(
                title = if (note.title == "Processing..." || note.title.isBlank()) {
                    "Draft note"
                } else {
                    note.title
                },
                summary = detail?.take(180).orEmpty(),
                status = NoteStatus.DRAFT
            )
        )
    }

    private suspend fun markFailed(note: NoteEntity, detail: String?) {
        noteDao.updateNote(
            note.copy(
                title = if (note.title == "Processing..." || note.title.isBlank()) {
                    "Failed note"
                } else {
                    note.title
                },
                summary = detail?.take(180).orEmpty(),
                status = NoteStatus.FAILED
            )
        )
    }

}
