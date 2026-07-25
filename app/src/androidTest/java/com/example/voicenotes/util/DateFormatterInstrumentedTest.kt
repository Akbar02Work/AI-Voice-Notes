package com.example.voicenotes.util

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DateFormatterInstrumentedTest {
    private val appContext: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun todayUsesEnglishApplicationLocale() {
        val formatter = DateFormatter(contextFor(Locale.ENGLISH))

        assertTrue(formatter.formatTimestamp(todayTimestamp()).startsWith("Today,"))
    }

    @Test
    fun todayUsesRussianApplicationLocale() {
        val formatter = DateFormatter(contextFor(Locale("ru")))

        assertTrue(formatter.formatTimestamp(todayTimestamp()).startsWith("Сегодня,"))
    }

    private fun contextFor(locale: Locale): Context {
        val configuration = Configuration(appContext.resources.configuration)
        configuration.setLocale(locale)
        return appContext.createConfigurationContext(configuration)
    }

    private fun todayTimestamp(): Long =
        LocalDate.now()
            .atTime(LocalTime.NOON)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
