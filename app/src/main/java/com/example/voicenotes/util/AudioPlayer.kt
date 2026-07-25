package com.example.voicenotes.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Управление воспроизведением аудио с реактивным состоянием для UI.
 * Duration доступна сразу после [prepareFile], ещё до нажатия Play.
 */
class AudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var preparedPath: String? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _playerState = MutableStateFlow(AudioPlayerState())
    val playerState: StateFlow<AudioPlayerState> = _playerState.asStateFlow()

    /**
     * Готовит файл к воспроизведению и сразу кладёт duration в state (без старта).
     */
    fun prepareFile(file: File) {
        if (!file.exists()) {
            _playerState.update {
                it.copy(
                    isPlaying = false,
                    currentPosition = 0,
                    duration = 0,
                    error = AppError.Unknown("Audio file not found")
                )
            }
            return
        }

        val path = file.absolutePath
        if (preparedPath == path && mediaPlayer != null) {
            // Уже готов — обновим duration на всякий случай
            _playerState.update {
                it.copy(
                    duration = mediaPlayer?.duration?.takeIf { d -> d > 0 } ?: it.duration,
                    currentPosition = 0,
                    isPlaying = false,
                    error = null
                )
            }
            return
        }

        scope.launch {
            val durationMs = withContext(Dispatchers.IO) { readDurationMs(file) }
            if (preparedPath == path && mediaPlayer != null) return@launch

            try {
                releasePlayer()
                val player = MediaPlayer().apply {
                    setDataSource(context, Uri.fromFile(file))
                    setOnCompletionListener {
                        stopProgressTracker()
                        _playerState.update { state ->
                            state.copy(isPlaying = false, currentPosition = 0)
                        }
                    }
                    prepare()
                }
                mediaPlayer = player
                preparedPath = path

                val resolvedDuration = player.duration.takeIf { it > 0 } ?: durationMs
                _playerState.update {
                    it.copy(
                        isPlaying = false,
                        currentPosition = 0,
                        duration = resolvedDuration,
                        error = null
                    )
                }
            } catch (e: IOException) {
                releasePlayer()
                _playerState.update {
                    it.copy(
                        isPlaying = false,
                        currentPosition = 0,
                        duration = durationMs,
                        error = AppError.Unknown("Failed to load audio: ${e.message}")
                    )
                }
            }
        }
    }

    fun playFile(file: File) {
        if (!file.exists()) {
            _playerState.update { it.copy(error = AppError.Unknown("Audio file not found")) }
            return
        }

        val path = file.absolutePath
        val existing = mediaPlayer
        if (existing != null && preparedPath == path) {
            try {
                if (existing.currentPosition >= existing.duration && existing.duration > 0) {
                    existing.seekTo(0)
                }
                existing.start()
                _playerState.update {
                    it.copy(
                        isPlaying = true,
                        duration = existing.duration.takeIf { d -> d > 0 } ?: it.duration,
                        error = null
                    )
                }
                startProgressTracker()
                return
            } catch (_: IllegalStateException) {
                // fall through to full reload
            }
        }

        stop(resetDuration = true)
        try {
            val player = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(file))
                setOnCompletionListener {
                    stopProgressTracker()
                    _playerState.update { state ->
                        state.copy(isPlaying = false, currentPosition = 0)
                    }
                }
                prepare()
                start()
            }
            mediaPlayer = player
            preparedPath = path
            _playerState.update {
                it.copy(
                    isPlaying = true,
                    duration = player.duration,
                    currentPosition = 0,
                    error = null
                )
            }
            startProgressTracker()
        } catch (e: IOException) {
            releasePlayer()
            _playerState.update {
                it.copy(error = AppError.Unknown("Failed to play audio: ${e.message}"))
            }
        }
    }

    fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _playerState.update { it.copy(isPlaying = false) }
                stopProgressTracker()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
                _playerState.update { it.copy(isPlaying = true) }
                startProgressTracker()
            }
        }
    }

    fun stop(resetDuration: Boolean = false) {
        val keptDuration = if (resetDuration) 0 else _playerState.value.duration
        releasePlayer()
        stopProgressTracker()
        _playerState.update {
            it.copy(
                isPlaying = false,
                currentPosition = 0,
                duration = keptDuration,
                error = null
            )
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        _playerState.update { it.copy(currentPosition = position) }
    }

    fun release() {
        stop(resetDuration = true)
        preparedPath = null
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (_: Exception) {
            // ignore cleanup races
        }
        mediaPlayer = null
        preparedPath = null
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                val current = mediaPlayer?.currentPosition ?: 0
                _playerState.update { it.copy(currentPosition = current) }
                delay(100)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun readDurationMs(file: File): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toIntOrNull()
                ?.coerceAtLeast(0)
                ?: 0
        } catch (_: Exception) {
            0
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // ignore
            }
        }
    }
}

data class AudioPlayerState(
    val isPlaying: Boolean = false,
    val currentPosition: Int = 0,
    val duration: Int = 0,
    val error: AppError? = null
)
