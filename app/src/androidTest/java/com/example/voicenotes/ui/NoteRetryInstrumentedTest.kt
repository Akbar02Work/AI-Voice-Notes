package com.example.voicenotes.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.voicenotes.NoteListCard
import com.example.voicenotes.NoteUi
import com.example.voicenotes.data.NoteStatus
import com.example.voicenotes.ui.theme.AIVoiceNotesTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NoteRetryInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun failedRecordingShowsRetryAndInvokesRecovery() {
        var retried = false

        composeRule.setContent {
            AIVoiceNotesTheme {
                NoteListCard(
                    note = NoteUi(
                        id = 1,
                        title = "Processing failed",
                        rawText = "",
                        summary = "Network unavailable",
                        formattedDate = "Today, 13:37",
                        previewText = "",
                        status = NoteStatus.FAILED
                    ),
                    onClick = {},
                    onRetry = { retried = true }
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Retry processing note")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(retried)
        }
    }
}
