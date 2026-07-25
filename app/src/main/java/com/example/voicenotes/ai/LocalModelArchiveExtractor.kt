package com.example.voicenotes.ai

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

class LocalModelArchiveExtractor {

    suspend fun extract(
        archiveFile: File,
        outputDirectory: File,
        rootDirectory: String,
        expectedFiles: List<LocalModelFile>
    ) {
        val expectedByPath = expectedFiles.associateBy { expected ->
            "$rootDirectory/${expected.name}"
        }
        val extracted = mutableSetOf<String>()

        TarArchiveInputStream(
            BZip2CompressorInputStream(
                BufferedInputStream(FileInputStream(archiveFile))
            )
        ).use { tar ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = tar.nextEntry ?: break
                val expected = expectedByPath[entry.name] ?: continue
                if (!entry.isFile || entry.size != expected.sizeBytes) {
                    throw IOException("Unexpected archive entry for ${expected.name}")
                }

                val partial = File(outputDirectory, "${expected.name}.extracting")
                FileOutputStream(partial, false).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = tar.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
                if (!isValid(partial, expected)) {
                    partial.delete()
                    throw IOException("Checksum verification failed for ${expected.name}")
                }
                val destination = File(outputDirectory, expected.name)
                destination.delete()
                if (!partial.renameTo(destination)) {
                    throw IOException("Unable to install ${expected.name}")
                }
                extracted += expected.name
            }
        }

        val missing = expectedFiles.map(LocalModelFile::name).filterNot(extracted::contains)
        if (missing.isNotEmpty()) {
            throw IOException("Archive is missing: ${missing.joinToString()}")
        }
    }

    private fun isValid(file: File, expected: LocalModelFile): Boolean =
        file.isFile &&
            file.length() == expected.sizeBytes &&
            sha256(file) == expected.sha256

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
