package com.example.voicenotes.ai

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAiService @Inject constructor(
    private val modelManager: LocalModelManager,
    private val transcriber: SherpaOnnxTranscriber,
    private val textProcessor: LocalTextProcessor
) {
    suspend fun transcribe(audio: AudioInput, modelId: String?): String {
        val model = modelManager.installedModel(modelId)
        return transcriber.transcribe(audio.file, model)
    }

    fun summarize(text: String): AiSummary = textProcessor.summarize(text)
}

class LocalInferenceException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
