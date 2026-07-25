package com.example.voicenotes.ai

import android.media.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class AndroidAudioDecoderTest {

    @Test
    fun `16 bit stereo PCM is downmixed to mono`() {
        val buffer = ByteBuffer.allocate(Short.SIZE_BYTES * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(16_384)
            .putShort(0)
            .putShort(-16_384)
            .putShort(0)
            .flip() as ByteBuffer

        val samples = AndroidAudioDecoder().decodePcm(
            buffer = buffer,
            channelCount = 2,
            encoding = AudioFormat.ENCODING_PCM_16BIT
        )

        assertArrayEquals(floatArrayOf(0.25f, -0.25f), samples, 0.0001f)
    }
}
