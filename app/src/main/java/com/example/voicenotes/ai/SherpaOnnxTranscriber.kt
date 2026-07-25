package com.example.voicenotes.ai

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SherpaOnnxTranscriber @Inject constructor(
    private val audioDecoder: AndroidAudioDecoder
) {
    suspend fun transcribe(audioFile: File, installedModel: InstalledLocalModel): String =
        withContext(Dispatchers.Default) {
            try {
                val text = transcribeOffline(audioFile, installedModel).trim()
                if (text.isBlank()) {
                    throw LocalInferenceException("No speech was recognized in the recording")
                }
                text
            } catch (error: LocalInferenceException) {
                throw error
            } catch (error: Exception) {
                throw LocalInferenceException(
                    "Offline transcription failed",
                    error
                )
            }
        }

    private fun transcribeOffline(
        audioFile: File,
        installedModel: InstalledLocalModel
    ): String {
        val directory = installedModel.directory
        val recognizer = OfflineRecognizer(
            assetManager = null,
            config = OfflineRecognizerConfig(
                featConfig = featureConfig(),
                modelConfig = OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(
                        encoder = File(directory, "encoder.int8.onnx").absolutePath,
                        decoder = File(directory, "decoder.onnx").absolutePath,
                        joiner = File(directory, "joiner.int8.onnx").absolutePath
                    ),
                    tokens = File(directory, "tokens.txt").absolutePath,
                    numThreads = inferenceThreads(),
                    provider = "cpu"
                ),
                decodingMethod = "greedy_search"
            )
        )
        val stream = recognizer.createStream()
        return try {
            audioDecoder.decode(audioFile) { samples, sampleRate ->
                stream.acceptWaveform(samples, sampleRate)
            }
            recognizer.decode(stream)
            recognizer.getResult(stream).text
        } finally {
            stream.release()
            recognizer.release()
        }
    }

    private fun featureConfig() = FeatureConfig(
        sampleRate = 16_000,
        featureDim = 80,
        dither = 0f
    )

    private fun inferenceThreads(): Int =
        Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
}
