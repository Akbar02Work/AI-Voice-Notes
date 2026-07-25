package com.example.voicenotes

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.voicenotes.navigation.Screen
import com.example.voicenotes.ui.NoteDetailsScreen
import com.example.voicenotes.ui.SetupScreen
import com.example.voicenotes.ui.SettingsScreen
import com.example.voicenotes.ai.AiProvider
import com.example.voicenotes.ai.CloudProviderCatalog
import com.example.voicenotes.ai.InferenceMode
import com.example.voicenotes.data.ThemeMode
import com.example.voicenotes.ui.theme.VoiceNotesMotion
import com.example.voicenotes.ui.theme.VoiceNotesTheme
import com.example.voicenotes.util.RecordingStorage
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val recordingsDir = RecordingStorage.directory(this)

        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val userPrefsState by settingsViewModel.userPreferences.collectAsState()

            VoiceNotesTheme(
                themeMode = userPrefsState?.themeMode ?: ThemeMode.SYSTEM,
                dynamicColor = userPrefsState?.useDynamicColor ?: true
            ) {
                val navController = rememberNavController()
                val notesViewModel: NotesViewModel = hiltViewModel()
                val modelDiscovery by settingsViewModel.modelDiscovery.collectAsState()
                val localModels by settingsViewModel.localModels.collectAsState()
                val secureStorageError by settingsViewModel.secureStorageError.collectAsState()

                // Фон чтобы не было белой вспышки при переходах
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (userPrefsState == null) {
                        // Loading state (можно пустой экран или сплэш)
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    } else if (userPrefsState?.isOnboardingCompleted == false) {
                        var inferenceModeName by rememberSaveable {
                            mutableStateOf(
                                (userPrefsState?.inferenceMode ?: InferenceMode.CLOUD).name
                            )
                        }
                        val inferenceMode = InferenceMode.valueOf(inferenceModeName)

                        var selectedProviderId by rememberSaveable(
                            userPrefsState?.selectedProvider?.name
                        ) {
                            mutableStateOf(
                                CloudProviderCatalog
                                    .findByProvider(
                                        userPrefsState?.selectedProvider ?: AiProvider.GEMINI
                                    )?.id ?: "gemini"
                            )
                        }

                        var selectedLocalModelId by rememberSaveable {
                            mutableStateOf(userPrefsState?.selectedLocalModelId)
                        }

                        var apiKey by rememberSaveable(selectedProviderId) {
                            val provider = CloudProviderCatalog.findById(selectedProviderId)?.provider
                            mutableStateOf(
                                when (provider) {
                                    AiProvider.GEMINI -> userPrefsState?.geminiApiKey.orEmpty()
                                    AiProvider.OPENAI -> userPrefsState?.openaiApiKey.orEmpty()
                                    AiProvider.GROQ -> userPrefsState?.groqApiKey.orEmpty()
                                    null -> ""
                                }
                            )
                        }
                        var selectedTranscriptionModel by rememberSaveable(selectedProviderId) {
                            mutableStateOf<String?>(null)
                        }
                        var selectedSummaryModel by rememberSaveable(selectedProviderId) {
                            mutableStateOf<String?>(null)
                        }
                        var isApiKeyVisible by rememberSaveable { mutableStateOf(false) }
                        val isSaving by settingsViewModel.isSaving.collectAsState()
                        val keyboardController = LocalSoftwareKeyboardController.current
                        val uriHandler = LocalUriHandler.current

                        LaunchedEffect(modelDiscovery.models, modelDiscovery.provider) {
                            if (
                                modelDiscovery.provider ==
                                CloudProviderCatalog.findById(selectedProviderId)?.provider
                            ) {
                                modelDiscovery.models?.let { models ->
                                    selectedTranscriptionModel =
                                        selectedTranscriptionModel
                                            ?.takeIf { selected ->
                                                models.transcription.any { it.id == selected }
                                            }
                                            ?: models.transcription.firstOrNull()?.id
                                    selectedSummaryModel =
                                        selectedSummaryModel
                                            ?.takeIf { selected ->
                                                models.summarization.any { it.id == selected }
                                            }
                                            ?: models.summarization.firstOrNull()?.id
                                }
                            }
                        }

                        SetupScreen(
                            inferenceMode = inferenceMode,
                            selectedProviderId = selectedProviderId,
                            selectedLocalModelId = selectedLocalModelId,
                            localModelStates = localModels,
                            apiKey = apiKey,
                            availableModels = modelDiscovery.models
                                ?.takeIf {
                                    modelDiscovery.provider ==
                                        CloudProviderCatalog.findById(selectedProviderId)?.provider
                                },
                            selectedTranscriptionModel = selectedTranscriptionModel,
                            selectedSummaryModel = selectedSummaryModel,
                            isCheckingKey = modelDiscovery.isLoading,
                            modelError = if (secureStorageError) {
                                stringResource(R.string.settings_secure_storage_error)
                            } else {
                                modelDiscovery.error
                            },
                            isApiKeyVisible = isApiKeyVisible,
                            isSaving = isSaving,
                            onInferenceModeSelected = { mode ->
                                inferenceModeName = mode.name
                            },
                            onProviderSelected = { option ->
                                selectedProviderId = option.id
                                apiKey = when (option.provider) {
                                    AiProvider.GEMINI -> userPrefsState?.geminiApiKey.orEmpty()
                                    AiProvider.OPENAI -> userPrefsState?.openaiApiKey.orEmpty()
                                    AiProvider.GROQ -> userPrefsState?.groqApiKey.orEmpty()
                                }
                                selectedTranscriptionModel = null
                                selectedSummaryModel = null
                                settingsViewModel.clearModelDiscovery()
                            },
                            onLocalModelSelected = { modelId ->
                                selectedLocalModelId = modelId
                            },
                            onLocalModelDownload = settingsViewModel::downloadLocalModel,
                            onLocalModelPause = settingsViewModel::pauseLocalModel,
                            onLocalModelCancel = settingsViewModel::cancelLocalModel,
                            onLocalModelDelete = { modelId ->
                                if (selectedLocalModelId == modelId) {
                                    selectedLocalModelId = null
                                }
                                settingsViewModel.deleteLocalModel(modelId)
                            },
                            onApiKeyChanged = {
                                apiKey = it
                                settingsViewModel.clearSecureStorageError()
                                selectedTranscriptionModel = null
                                selectedSummaryModel = null
                                settingsViewModel.clearModelDiscovery()
                            },
                            onCheckApiKey = {
                                CloudProviderCatalog.findById(selectedProviderId)?.provider?.let {
                                    settingsViewModel.validateProviderKey(it, apiKey)
                                }
                            },
                            onTranscriptionModelSelected = {
                                selectedTranscriptionModel = it.id
                            },
                            onSummaryModelSelected = {
                                selectedSummaryModel = it.id
                            },
                            onApiKeyVisibilityToggle = { isApiKeyVisible = !isApiKeyVisible },
                            onGetStartedClick = {
                                when (inferenceMode) {
                                    InferenceMode.CLOUD -> {
                                        val provider = CloudProviderCatalog
                                            .findById(selectedProviderId)
                                            ?.provider
                                        if (
                                            provider != null &&
                                            apiKey.isNotBlank() &&
                                            selectedTranscriptionModel != null &&
                                            selectedSummaryModel != null
                                        ) {
                                            keyboardController?.hide()
                                            settingsViewModel.completeCloudSetup(
                                                provider,
                                                apiKey.trim(),
                                                selectedTranscriptionModel!!,
                                                selectedSummaryModel!!
                                            )
                                        }
                                    }
                                    InferenceMode.LOCAL -> {
                                        if (selectedLocalModelId != null) {
                                            keyboardController?.hide()
                                            settingsViewModel.completeLocalSetup(selectedLocalModelId)
                                        }
                                    }
                                }
                            },
                            onSkipClick = {
                                keyboardController?.hide()
                                settingsViewModel.completeOnboarding()
                            },
                            onFindApiKeyClick = {
                                CloudProviderCatalog.findById(selectedProviderId)?.apiKeyUrl
                                    ?.let { uriHandler.openUri(it) }
                            }
                        )
                    } else {
                        NavHost(
                        navController = navController,
                        startDestination = Screen.NotesList.route,
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                        // Emphasized slide (no fade) — avoids white flash between screens
                        enterTransition = {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = VoiceNotesMotion.navTween()
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = VoiceNotesMotion.navTween()
                            )
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = VoiceNotesMotion.navTween()
                            )
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = VoiceNotesMotion.navTween()
                            )
                        }
                    ) {
                        // Экран списка заметок
                        composable(Screen.NotesList.route) {
                            NotesListScreen(
                                viewModel = notesViewModel,
                                recordingsDir = recordingsDir,
                                onNoteClick = { noteId ->
                                    navController.navigate(Screen.NoteDetails.createRoute(noteId))
                                },
                                onSettingsClick = {
                                    navController.navigate(Screen.Settings.route)
                                }
                            )
                        }

                        // Экран деталей заметки
                        composable(
                            route = Screen.NoteDetails.route,
                            arguments = listOf(navArgument("noteId") { type = NavType.LongType })
                        ) {
                            NoteDetailsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        
                        // Экран настроек
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        }
                    } // NavHost
                } // Surface (else)
            } // Surface (loading/onboarding check)

        }
    }
}
