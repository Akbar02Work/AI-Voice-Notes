package com.example.voicenotes.ai

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalTextProcessor @Inject constructor() {

    fun summarize(source: String): AiSummary {
        val text = source.replace(WHITESPACE, " ").trim()
        if (text.isBlank()) {
            throw LocalInferenceException("Cannot summarize an empty transcript")
        }

        val title = createTitle(text)
        val summary = if (text.length <= SHORT_TEXT_LIMIT) {
            text
        } else {
            selectSummaryUnits(splitIntoUnits(text)).joinToString(" ").trim()
        }
        return AiSummary(
            title = title,
            summary = summary.ifBlank { text.take(SUMMARY_FALLBACK_LIMIT).trim() }
        )
    }

    internal fun createTitle(text: String): String {
        val firstUnit = splitIntoUnits(text).firstOrNull().orEmpty()
        val words = WORD.findAll(firstUnit)
            .map(MatchResult::value)
            .filterNot { it.lowercase() in FILLER_WORDS }
            .take(TITLE_WORDS)
            .toList()
        val title = words.joinToString(" ").ifBlank {
            WORD.findAll(text).map(MatchResult::value).take(TITLE_WORDS).joinToString(" ")
        }
        return title
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            .take(TITLE_MAX_CHARS)
            .trimEnd(' ', ',', '.', ':', ';', '-')
    }

    internal fun splitIntoUnits(text: String): List<String> {
        val sentences = text
            .split(SENTENCE_BOUNDARY)
            .map(String::trim)
            .filter(String::isNotBlank)
        if (sentences.size > 1) return sentences

        val words = text.split(" ").filter(String::isNotBlank)
        if (words.size <= UNIT_WORDS) return listOf(text)
        return words.chunked(UNIT_WORDS).map { chunk -> chunk.joinToString(" ") }
    }

    private fun selectSummaryUnits(units: List<String>): List<String> {
        if (units.size <= SUMMARY_UNITS) return units
        val unitTokens = units.map(::significantTokens)
        val frequencies = unitTokens
            .flatten()
            .groupingBy { it }
            .eachCount()
        val maxFrequency = frequencies.values.maxOrNull()?.coerceAtLeast(1) ?: 1

        val ranked = units.indices.sortedByDescending { index ->
            val tokens = unitTokens[index]
            val frequencyScore = if (tokens.isEmpty()) {
                0.0
            } else {
                tokens.sumOf { (frequencies[it] ?: 0).toDouble() / maxFrequency } / tokens.size
            }
            frequencyScore +
                if (index == 0) 0.25 else 0.0 +
                if (DIGIT.containsMatchIn(units[index])) 0.15 else 0.0 +
                if (tokens.any { it in ACTION_WORDS }) 0.20 else 0.0
        }

        val selected = mutableListOf<Int>()
        for (candidate in ranked) {
            val isDuplicate = selected.any { existing ->
                jaccard(unitTokens[candidate], unitTokens[existing]) >= MAX_SIMILARITY
            }
            if (!isDuplicate) selected += candidate
            if (selected.size == SUMMARY_UNITS) break
        }
        return selected.sorted().map(units::get)
    }

    private fun significantTokens(text: String): Set<String> =
        WORD.findAll(text)
            .map { it.value.lowercase() }
            .filter { it.length > 2 && it !in STOP_WORDS }
            .toSet()

    private fun jaccard(first: Set<String>, second: Set<String>): Double {
        if (first.isEmpty() || second.isEmpty()) return 0.0
        return first.intersect(second).size.toDouble() / first.union(second).size
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val SENTENCE_BOUNDARY = Regex("(?<=[.!?…])\\s+")
        val WORD = Regex("[\\p{L}\\p{N}]+")
        val DIGIT = Regex("\\d")
        const val TITLE_WORDS = 9
        const val TITLE_MAX_CHARS = 60
        const val SHORT_TEXT_LIMIT = 300
        const val SUMMARY_FALLBACK_LIMIT = 700
        const val UNIT_WORDS = 24
        const val SUMMARY_UNITS = 3
        const val MAX_SIMILARITY = 0.65

        val FILLER_WORDS = setOf(
            "ну", "вот", "короче", "значит", "как", "бы", "um", "uh", "well", "like"
        )
        val STOP_WORDS = setOf(
            "это", "как", "что", "для", "или", "она", "они", "его", "еще", "уже", "был",
            "была", "были", "есть", "так", "там", "тут", "вот", "при", "про", "под", "над",
            "the", "and", "that", "this", "with", "from", "have", "has", "was", "were", "are",
            "but", "not", "you", "your", "for", "about"
        )
        val ACTION_WORDS = setOf(
            "нужно", "надо", "сделать", "купить", "проверить", "обсудить", "идея", "важно",
            "must", "need", "todo", "buy", "check", "discuss", "idea", "important"
        )
    }
}
