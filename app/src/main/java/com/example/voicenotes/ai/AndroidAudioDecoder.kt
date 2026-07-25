package com.example.voicenotes.ai

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

class AndroidAudioDecoder @Inject constructor() {

    fun decode(
        audioFile: File,
        onPcmChunk: (samples: FloatArray, sampleRate: Int) -> Unit
    ) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(audioFile.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: throw LocalInferenceException("The recording has no audio track")

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw LocalInferenceException("Unknown recording format")
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            decodeTrack(extractor, codec, inputFormat, onPcmChunk)
        } catch (error: LocalInferenceException) {
            throw error
        } catch (error: Exception) {
            throw LocalInferenceException(
                "Unable to decode the recording for offline transcription",
                error
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun decodeTrack(
        extractor: MediaExtractor,
        codec: MediaCodec,
        inputFormat: MediaFormat,
        onPcmChunk: (FloatArray, Int) -> Unit
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputEnded = false
        var outputEnded = false
        var outputFormat = inputFormat

        while (!outputEnded) {
            if (!inputEnded) {
                val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                        ?: throw LocalInferenceException("Audio decoder input is unavailable")
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputEnded = true
                    } else {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            sampleSize,
                            extractor.sampleTime,
                            0
                        )
                        extractor.advance()
                    }
                }
            }

            when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                else -> if (outputIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                            ?: throw LocalInferenceException("Audio decoder output is unavailable")
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val sampleRate = outputFormat.intValue(
                            MediaFormat.KEY_SAMPLE_RATE,
                            DEFAULT_SAMPLE_RATE
                        )
                        val channels = outputFormat.intValue(MediaFormat.KEY_CHANNEL_COUNT, 1)
                        val encoding = outputFormat.intValue(
                            MediaFormat.KEY_PCM_ENCODING,
                            AudioFormat.ENCODING_PCM_16BIT
                        )
                        onPcmChunk(
                            decodePcm(outputBuffer.slice(), channels, encoding),
                            sampleRate
                        )
                    }
                    outputEnded =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
    }

    internal fun decodePcm(
        buffer: ByteBuffer,
        channelCount: Int,
        encoding: Int
    ): FloatArray {
        require(channelCount > 0)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val interleaved = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> FloatArray(buffer.remaining() / Float.SIZE_BYTES) {
                buffer.float.coerceIn(-1f, 1f)
            }
            AudioFormat.ENCODING_PCM_16BIT -> FloatArray(buffer.remaining() / Short.SIZE_BYTES) {
                buffer.short / 32768f
            }
            AudioFormat.ENCODING_PCM_8BIT -> FloatArray(buffer.remaining()) {
                ((buffer.get().toInt() and 0xff) - 128) / 128f
            }
            else -> throw LocalInferenceException("Unsupported PCM encoding: $encoding")
        }

        if (channelCount == 1) return interleaved
        val frames = interleaved.size / channelCount
        return FloatArray(frames) { frame ->
            var sum = 0f
            for (channel in 0 until channelCount) {
                sum += interleaved[frame * channelCount + channel]
            }
            sum / channelCount
        }
    }

    private fun MediaFormat.intValue(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback

    private companion object {
        const val CODEC_TIMEOUT_US = 10_000L
        const val DEFAULT_SAMPLE_RATE = 16_000
    }
}
