package com.example.voicenotes.util

import android.content.Context
import com.example.voicenotes.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Formats note timestamps using the active application locale.
 */
@Singleton
class DateFormatter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun formatTimestamp(timestamp: Long): String {
        val locale = currentLocale()
        val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(locale)
        val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
        val fullFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        val dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        )
        val date = dateTime.toLocalDate()
        val today = LocalDate.now()
        val time = dateTime.format(timeFormatter)

        return when (date) {
            today -> context.getString(R.string.date_today_format, time)
            today.minusDays(1) -> context.getString(R.string.date_yesterday_format, time)
            else -> {
                if (date.year == today.year) {
                    "${dateTime.format(dayMonthFormatter)}, $time"
                } else {
                    "${dateTime.format(fullFormatter)}, $time"
                }
            }
        }
    }

    private fun currentLocale(): Locale {
        val locales = context.resources.configuration.locales
        return if (locales.isEmpty) Locale.getDefault() else locales[0]
    }
}
