package com.example.voicenotes.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.voicenotes.ai.CloudProviderCatalog
import com.example.voicenotes.ai.InferenceMode
import com.example.voicenotes.ui.theme.AIVoiceNotesTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SetupScreenInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboardingCanSwitchModeAndSkipWithoutCredentials() {
        var selectedMode = InferenceMode.CLOUD
        var skipped = false

        composeRule.setContent {
            AIVoiceNotesTheme {
                SetupScreen(
                    inferenceMode = selectedMode,
                    selectedProviderId = CloudProviderCatalog.options.first().id,
                    selectedLocalModelId = null,
                    localModelStates = emptyList(),
                    apiKey = "",
                    availableModels = null,
                    selectedTranscriptionModel = null,
                    selectedSummaryModel = null,
                    isCheckingKey = false,
                    modelError = null,
                    isApiKeyVisible = false,
                    isSaving = false,
                    onInferenceModeSelected = { selectedMode = it },
                    onProviderSelected = {},
                    onLocalModelSelected = {},
                    onLocalModelDownload = {},
                    onLocalModelPause = {},
                    onLocalModelCancel = {},
                    onLocalModelDelete = {},
                    onApiKeyChanged = {},
                    onCheckApiKey = {},
                    onTranscriptionModelSelected = {},
                    onSummaryModelSelected = {},
                    onApiKeyVisibilityToggle = {},
                    onGetStartedClick = {},
                    onSkipClick = { skipped = true },
                    onFindApiKeyClick = {}
                )
            }
        }

        composeRule.onNodeWithText("On device").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertTrue(selectedMode == InferenceMode.LOCAL)
        }

        composeRule.onNodeWithText("Skip for now").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertTrue(skipped)
        }
    }
}
