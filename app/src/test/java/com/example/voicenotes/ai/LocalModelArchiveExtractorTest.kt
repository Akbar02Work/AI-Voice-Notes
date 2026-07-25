package com.example.voicenotes.ai

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalModelArchiveExtractorTest {

    @Test
    fun `extracts and verifies only manifest files`() = runTest {
        val directory = createTempDirectory("voicenotes-archive-").toFile()
        val archive = File(directory, "model.tar.bz2")
        val encoder = byteArrayOf(1, 2, 3, 4)
        val tokens = "а 1\nб 2\n".encodeToByteArray()
        writeArchive(
            archive = archive,
            entries = mapOf(
                "model/encoder.int8.onnx" to encoder,
                "model/tokens.txt" to tokens,
                "model/unused-fp32.onnx" to ByteArray(1024)
            )
        )

        try {
            LocalModelArchiveExtractor().extract(
                archiveFile = archive,
                outputDirectory = directory,
                rootDirectory = "model",
                expectedFiles = listOf(
                    LocalModelFile("encoder.int8.onnx", encoder.size.toLong(), sha256(encoder)),
                    LocalModelFile("tokens.txt", tokens.size.toLong(), sha256(tokens))
                )
            )

            assertArrayEquals(encoder, File(directory, "encoder.int8.onnx").readBytes())
            assertArrayEquals(tokens, File(directory, "tokens.txt").readBytes())
            assertFalse(File(directory, "unused-fp32.onnx").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun writeArchive(archive: File, entries: Map<String, ByteArray>) {
        TarArchiveOutputStream(
            BZip2CompressorOutputStream(FileOutputStream(archive))
        ).use { tar ->
            entries.forEach { (name, content) ->
                val entry = TarArchiveEntry(name).apply { size = content.size.toLong() }
                tar.putArchiveEntry(entry)
                tar.write(content)
                tar.closeArchiveEntry()
            }
            tar.finish()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
