package com.example.voicenotes.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTextProcessorTest {
    private val processor = LocalTextProcessor()

    @Test
    fun `short transcript stays verbatim in summary`() {
        val source = "Нужно купить билеты завтра и обсудить время поездки."

        val result = processor.summarize(source)

        assertEquals(source, result.summary)
        assertTrue(result.title.startsWith("Нужно купить билеты"))
    }

    @Test
    fun `long summary only uses source text units`() {
        val source = listOf(
            "Сегодня обсудили план выпуска приложения.",
            "Нужно проверить сборку на телефоне до пятницы.",
            "Команда также посмотрит расход батареи.",
            "Отдельно решили не добавлять новые функции.",
            "Следующая встреча назначена на 28 июля.",
            "После теста нужно записать результаты."
        ).joinToString(" ")

        val repeatedSource = "$source $source"
        val result = processor.summarize(repeatedSource)
        val originalSentences = processor.splitIntoUnits(repeatedSource).toSet()

        processor.splitIntoUnits(result.summary).forEach { selected ->
            assertTrue(selected in originalSentences)
        }
        assertFalse(result.summary.isBlank())
    }

    @Test
    fun `title skips common filler and stays bounded`() {
        val source = "ну вот короче нужно проверить очень длинную запись перед выпуском приложения"

        val title = processor.createTitle(source)

        assertTrue(title.startsWith("Нужно проверить"))
        assertTrue(title.length <= 60)
    }
}
