package com.example.voicenotes.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicenotes.NoteUi
import com.example.voicenotes.data.NoteEntity
import com.example.voicenotes.data.NoteRepository
import com.example.voicenotes.util.AppError
import com.example.voicenotes.util.AudioPlayer
import com.example.voicenotes.util.DateFormatter
import com.example.voicenotes.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NoteDetailsUiState(
    val note: NoteUi? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val isDeleted: Boolean = false
)

@HiltViewModel
class NoteDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NoteRepository,
    val audioPlayer: AudioPlayer,
    private val dateFormatter: DateFormatter
) : ViewModel() {

    private val noteId: Long = checkNotNull(savedStateHandle["noteId"])

    private val _error = MutableStateFlow<AppError?>(null)
    private val _isDeleted = MutableStateFlow(false)
    private var preparedAudioPath: String? = null

    private val noteFlow = repository.observeNoteById(noteId)
        .distinctUntilChanged()
        .onEach { entity ->
            val path = entity?.audioPath ?: return@onEach
            if (path != preparedAudioPath) {
                val file = File(path)
                if (file.exists()) {
                    audioPlayer.prepareFile(file)
                    preparedAudioPath = path
                }
            }
        }

    val uiState: StateFlow<NoteDetailsUiState> = combine(
        noteFlow,
        _error,
        _isDeleted
    ) { entity, error, isDeleted ->
        NoteDetailsUiState(
            note = entity?.toUi(),
            isLoading = false,
            error = error,
            isDeleted = isDeleted
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NoteDetailsUiState()
    )

    fun deleteNote() {
        viewModelScope.launch {
            try {
                audioPlayer.stop()
                val note = repository.getNoteById(noteId)
                note?.let {
                    val file = File(it.audioPath)
                    if (file.exists()) {
                        file.delete()
                    }
                }

                repository.deleteNote(noteId)
                _isDeleted.value = true
            } catch (e: Exception) {
                _error.value = AppError.DeleteFailed(e.message)
            }
        }
    }

    fun updateTitle(newTitle: String) {
        viewModelScope.launch {
            try {
                repository.updateNoteTitle(noteId, newTitle)
            } catch (e: Exception) {
                _error.value = AppError.UpdateFailed(e.message)
            }
        }
    }

    fun retryNote() {
        viewModelScope.launch {
            try {
                repository.retryNote(noteId)
            } catch (e: Exception) {
                _error.value = ErrorHandler.fromException(e)
            }
        }
    }

    fun playAudio() {
        viewModelScope.launch {
            val entity = repository.getNoteById(noteId) ?: return@launch
            val file = File(entity.audioPath)
            audioPlayer.playFile(file)
        }
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }

    private fun NoteEntity.toUi() = NoteUi(
        id = id,
        title = title,
        rawText = rawText,
        summary = summary,
        formattedDate = dateFormatter.formatTimestamp(timestamp),
        previewText = rawText.take(100).replace("\n", " ") +
            if (rawText.length > 100) "..." else "",
        status = status,
        isPinned = isPinned
    )
}
