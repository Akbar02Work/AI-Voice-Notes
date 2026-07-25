package com.example.voicenotes.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelCatalogTest {

    @Test
    fun `catalog exposes separate lite and standard profiles`() {
        assertEquals(2, LocalModelCatalog.models.size)
        assertEquals(
            setOf("zipformer-ru-lite-int8", "zipformer-ru-standard-int8"),
            LocalModelCatalog.models.map(LocalModelInfo::id).toSet()
        )
        assertTrue(LocalModelCatalog.models.first().installedSizeBytes <
            LocalModelCatalog.models.last().installedSizeBytes)
    }

    @Test
    fun `all downloads are immutable and checksum protected`() {
        LocalModelCatalog.models.forEach { model ->
            assertEquals(64, model.revision.length)
            assertFalse(model.archive.url.contains("/main/"))
            assertEquals(64, model.archive.sha256.length)
            assertTrue(model.archive.sizeBytes > model.installedSizeBytes)
            assertTrue(model.files.isNotEmpty())
            model.files.forEach { file ->
                assertTrue(file.sizeBytes > 0)
                assertEquals(64, file.sha256.length)
            }
        }
    }
}
