package com.example.voicenotes.ai

/**
 * Where voice notes are processed: cloud API or on-device models.
 */
enum class InferenceMode {
    CLOUD,
    LOCAL
}
