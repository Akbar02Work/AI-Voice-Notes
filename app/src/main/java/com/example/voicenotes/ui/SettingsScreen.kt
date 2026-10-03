package com.example.voicenotes.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.voicenotes.BuildConfig
import com.example.voicenotes.R
import com.example.voicenotes.SettingsViewModel
import com.example.voicenotes.ai.AiProvider
import com.example.voicenotes.ai.AiModel
import com.example.voicenotes.ai.CloudProviderCatalog
import com.example.voicenotes.ai.InferenceMode
import com.example.voicenotes.data.ThemeMode
import com.example.voicenotes.data.apiKeyFor
import com.example.voicenotes.ui.theme.AIVoiceNotesMotion
import com.example.voicenotes.ui.theme.AIVoiceNotesPillShape
import com.example.voicenotes.ui.theme.isDynamicColorSupported
import com.example.voicenotes.ui.theme.spacing

/**
 * Список поддерживаемых языков. SYSTEM = follow device locale (Android default).
 */
enum class AppLanguage(val code: String?, val displayNameResId: Int) {
    SYSTEM(null, R.string.settings_language_system),
    ENGLISH("en", R.string.settings_language_english),
    RUSSIAN("ru", R.string.settings_language_russian)
}

/**
 * Получить текущий язык приложения.
 * Empty AppCompat locales means "system default".
 */
fun getCurrentLanguage(): AppLanguage {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) {
        return AppLanguage.SYSTEM
    }
    val currentLocale = locales[0]?.language ?: return AppLanguage.SYSTEM
    return AppLanguage.entries.find { it.code == currentLocale } ?: AppLanguage.SYSTEM
}

/**
 * Установить язык приложения. SYSTEM clears the override so Android uses device language.
 */
fun setAppLanguage(language: AppLanguage) {
    val localeList = if (language.code == null) {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(language.code)
    }
    AppCompatDelegate.setApplicationLocales(localeList)
}

/**
 * Маскирует API ключ для отображения.
 * Показывает первые 4 и последние 4 символа.
 */
fun maskApiKey(key: String): String {
    if (key.length <= 8) return if (key.isNotEmpty()) "****" else ""
    return "${key.take(4)}****${key.takeLast(4)}"
}

private enum class ApiKeyStatus { NOT_SET, CHECKING, VALID, ERROR }

/**
 * Экран настроек: провайдер и ключ, модели, внешний вид, язык.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val userPreferencesState by viewModel.userPreferences.collectAsState()
    val modelDiscovery by viewModel.modelDiscovery.collectAsState()
    val localModels by viewModel.localModels.collectAsState()
    val secureStorageError by viewModel.secureStorageError.collectAsState()
    val userPreferences = userPreferencesState ?: return
    val spacing = MaterialTheme.spacing
    val snackbarHostState = remember { SnackbarHostState() }
    val secureStorageErrorMessage = stringResource(R.string.settings_secure_storage_error)

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showProviderDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showTranscriptionModelDialog by remember { mutableStateOf(false) }
    var showSummaryModelDialog by remember { mutableStateOf(false) }
    var currentLanguage by remember { mutableStateOf(getCurrentLanguage()) }

    LaunchedEffect(secureStorageError) {
        if (secureStorageError) {
            snackbarHostState.showSnackbar(secureStorageErrorMessage)
            viewModel.clearSecureStorageError()
        }
    }

    val provider = userPreferences.selectedProvider
    val currentApiKey = userPreferences.apiKeyFor(provider)
    val currentApiKeyTitle = when (provider) {
        AiProvider.GEMINI -> stringResource(R.string.settings_api_key_gemini)
        AiProvider.OPENAI -> stringResource(R.string.settings_api_key_openai)
        AiProvider.GROQ -> stringResource(R.string.settings_api_key_groq)
    }

    LaunchedEffect(provider, currentApiKey) {
        if (currentApiKey.isNotBlank()) {
            viewModel.validateProviderKey(provider, currentApiKey)
        } else {
            viewModel.clearModelDiscovery()
        }
    }

    LaunchedEffect(modelDiscovery.models, provider) {
        val models = modelDiscovery.models ?: return@LaunchedEffect
        if (modelDiscovery.provider != provider) return@LaunchedEffect
        val transcription = userPreferences.selectedTranscriptionModel
            ?.takeIf { selected -> models.transcription.any { it.id == selected } }
            ?: models.transcription.firstOrNull()?.id
        val summary = userPreferences.selectedSummaryModel
            ?.takeIf { selected -> models.summarization.any { it.id == selected } }
            ?: models.summarization.firstOrNull()?.id
        if (
            transcription != null &&
            summary != null &&
            (
                transcription != userPreferences.selectedTranscriptionModel ||
                    summary != userPreferences.selectedSummaryModel
                )
        ) {
            viewModel.setSelectedModels(transcription, summary)
        }
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = currentLanguage,
            onLanguageSelected = { language ->
                currentLanguage = language
                setAppLanguage(language)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showProviderDialog) {
        ProviderSelectionDialog(
            currentProvider = provider,
            onProviderSelected = { selected ->
                viewModel.setProvider(selected)
                showProviderDialog = false
            },
            onDismiss = { showProviderDialog = false }
        )
    }

    if (showApiKeyDialog) {
        ApiKeyInputDialog(
            title = currentApiKeyTitle,
            currentKey = currentApiKey,
            onSave = { key ->
                when (provider) {
                    AiProvider.GEMINI -> viewModel.setGeminiApiKey(key)
                    AiProvider.OPENAI -> viewModel.setOpenAiApiKey(key)
                    AiProvider.GROQ -> viewModel.setGroqApiKey(key)
                }
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false }
        )
    }

    if (showTranscriptionModelDialog) {
        ModelSelectionDialog(
            title = stringResource(R.string.settings_transcription_model),
            icon = Icons.Default.GraphicEq,
            models = modelDiscovery.models?.transcription.orEmpty(),
            currentModelId = userPreferences.selectedTranscriptionModel,
            onModelSelected = { model ->
                val summary = userPreferences.selectedSummaryModel
                    ?: modelDiscovery.models?.summarization?.firstOrNull()?.id
                if (summary != null) {
                    viewModel.setSelectedModels(model.id, summary)
                }
                showTranscriptionModelDialog = false
            },
            onDismiss = { showTranscriptionModelDialog = false }
        )
    }

    if (showSummaryModelDialog) {
        ModelSelectionDialog(
            title = stringResource(R.string.settings_summary_model),
            icon = Icons.Default.AutoAwesome,
            models = modelDiscovery.models?.summarization.orEmpty(),
            currentModelId = userPreferences.selectedSummaryModel,
            onModelSelected = { model ->
                val transcription = userPreferences.selectedTranscriptionModel
                    ?: modelDiscovery.models?.transcription?.firstOrNull()?.id
                if (transcription != null) {
                    viewModel.setSelectedModels(transcription, model.id)
                }
                showSummaryModelDialog = false
            },
            onDismiss = { showSummaryModelDialog = false }
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.note_details_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        val discoveryForProvider = modelDiscovery.takeIf { it.provider == provider }
        val providerModels = discoveryForProvider?.models
        val keyStatus = when {
            currentApiKey.isBlank() -> ApiKeyStatus.NOT_SET
            discoveryForProvider == null -> ApiKeyStatus.NOT_SET
            discoveryForProvider.isLoading -> ApiKeyStatus.CHECKING
            discoveryForProvider.error != null -> ApiKeyStatus.ERROR
            providerModels != null -> ApiKeyStatus.VALID
            else -> ApiKeyStatus.NOT_SET
        }
        val modelStatus = when (keyStatus) {
            ApiKeyStatus.CHECKING -> stringResource(R.string.settings_models_loading)
            ApiKeyStatus.ERROR -> stringResource(
                R.string.settings_models_error,
                discoveryForProvider?.error.orEmpty()
            )
            else -> stringResource(R.string.settings_models_not_loaded)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = spacing.screenHorizontal,
                    end = spacing.screenHorizontal,
                    bottom = spacing.huge
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.extraLarge)
        ) {
            SettingsSection(title = stringResource(R.string.settings_section_ai)) {
                Text(
                    text = stringResource(R.string.settings_inference_mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.large)
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.large)
                ) {
                    InferenceMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = userPreferences.inferenceMode == mode,
                            onClick = { viewModel.setInferenceMode(mode) },
                            enabled = mode == InferenceMode.CLOUD ||
                                localModels.any {
                                    it.isInstalled &&
                                        it.model.id == userPreferences.selectedLocalModelId
                                },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = InferenceMode.entries.size
                            ),
                            label = {
                                Text(
                                    stringResource(
                                        if (mode == InferenceMode.CLOUD) {
                                            R.string.settings_inference_cloud
                                        } else {
                                            R.string.settings_inference_local
                                        }
                                    )
                                )
                            }
                        )
                    }
                }

                RowDivider()

                SettingsRow(
                    icon = providerIcon(CloudProviderCatalog.findByProvider(provider)?.id),
                    title = stringResource(R.string.settings_provider_title),
                    subtitle = providerName(provider),
                    onClick = { showProviderDialog = true }
                )

                RowDivider()

                SettingsRow(
                    icon = Icons.Default.Key,
                    title = currentApiKeyTitle,
                    subtitle = maskApiKey(currentApiKey).ifEmpty {
                        stringResource(R.string.settings_api_key_not_set)
                    },
                    trailing = { ApiKeyStatusBadge(status = keyStatus) },
                    onClick = { showApiKeyDialog = true }
                )

                AnimatedVisibility(
                    visible = keyStatus == ApiKeyStatus.ERROR,
                    enter = fadeIn(animationSpec = AIVoiceNotesMotion.fadeTween()) +
                        expandVertically(animationSpec = AIVoiceNotesMotion.navTween()),
                    exit = fadeOut(animationSpec = AIVoiceNotesMotion.fadeTween()) +
                        shrinkVertically(animationSpec = AIVoiceNotesMotion.navTween())
                ) {
                    KeyErrorFooter(
                        message = discoveryForProvider?.error.orEmpty(),
                        onRetry = { viewModel.validateProviderKey(provider, currentApiKey) }
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.settings_section_models)) {
                SettingsRow(
                    icon = Icons.Default.GraphicEq,
                    title = stringResource(R.string.settings_transcription_model),
                    subtitle = userPreferences.selectedTranscriptionModel
                        ?.takeIf { providerModels != null }
                        ?: modelStatus,
                    enabled = providerModels != null,
                    onClick = { showTranscriptionModelDialog = true }
                )

                RowDivider()

                SettingsRow(
                    icon = Icons.Default.AutoAwesome,
                    title = stringResource(R.string.settings_summary_model),
                    subtitle = userPreferences.selectedSummaryModel
                        ?.takeIf { providerModels != null }
                        ?: modelStatus,
                    enabled = providerModels != null,
                    onClick = { showSummaryModelDialog = true }
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_offline_models)) {
                OfflineModelsPanel(
                    states = localModels,
                    activeModelId = userPreferences.selectedLocalModelId
                        .takeIf { userPreferences.inferenceMode == InferenceMode.LOCAL },
                    onDownload = viewModel::downloadLocalModel,
                    onPause = viewModel::pauseLocalModel,
                    onCancel = viewModel::cancelLocalModel,
                    onDelete = viewModel::deleteLocalModel,
                    onActivate = viewModel::activateLocalModel,
                    modifier = Modifier.padding(horizontal = spacing.large)
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                ThemeModeRow(
                    currentMode = userPreferences.themeMode,
                    onModeSelected = viewModel::setThemeMode
                )

                if (isDynamicColorSupported) {
                    RowDivider()

                    SettingsSwitchRow(
                        icon = Icons.Default.Palette,
                        title = stringResource(R.string.settings_dynamic_color_title),
                        subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                        checked = userPreferences.useDynamicColor,
                        onCheckedChange = viewModel::setUseDynamicColor
                    )
                }

                RowDivider()

                SettingsRow(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.settings_language_title),
                    subtitle = stringResource(currentLanguage.displayNameResId),
                    onClick = { showLanguageDialog = true }
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = spacing.large)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            content = { Column(content = content) }
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = spacing.large, vertical = spacing.medium)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.large)
    ) {
        RowIcon(icon = icon)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val spacing = MaterialTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = spacing.large, vertical = spacing.medium)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.large)
    ) {
        RowIcon(icon = icon)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ThemeModeRow(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.large, vertical = spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.large)
        ) {
            RowIcon(icon = themeModeIcon(currentMode))

            Text(
                text = stringResource(R.string.settings_theme_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == currentMode,
                    onClick = { onModeSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ThemeMode.entries.size
                    ),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = mode == currentMode) {
                            Icon(
                                imageVector = themeModeIcon(mode),
                                contentDescription = null,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                            )
                        }
                    },
                    label = { Text(text = stringResource(themeModeLabel(mode))) }
                )
            }
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Surface(
        shape = AIVoiceNotesPillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun ApiKeyStatusBadge(status: ApiKeyStatus) {
    if (status == ApiKeyStatus.NOT_SET) return

    val spacing = MaterialTheme.spacing
    val containerColor by animateColorAsState(
        targetValue = when (status) {
            ApiKeyStatus.VALID -> MaterialTheme.colorScheme.primaryContainer
            ApiKeyStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = AIVoiceNotesMotion.fadeTween(),
        label = "key_status_container"
    )
    val contentColor = when (status) {
        ApiKeyStatus.VALID -> MaterialTheme.colorScheme.onPrimaryContainer
        ApiKeyStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = AIVoiceNotesPillShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = spacing.small,
                vertical = spacing.extraSmall
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
        ) {
            when (status) {
                ApiKeyStatus.CHECKING -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
                ApiKeyStatus.VALID -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                else -> Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }

            Text(
                text = stringResource(
                    when (status) {
                        ApiKeyStatus.CHECKING -> R.string.settings_key_status_checking
                        ApiKeyStatus.VALID -> R.string.settings_key_status_valid
                        else -> R.string.settings_key_status_error
                    }
                ),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun KeyErrorFooter(message: String, onRetry: () -> Unit) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = spacing.large,
                end = spacing.large,
                bottom = spacing.medium
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )

        TextButton(
            onClick = onRetry,
            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.settings_models_retry))
        }
    }
}

/**
 * Диалог выбора AI провайдера.
 */
@Composable
fun ProviderSelectionDialog(
    currentProvider: AiProvider,
    onProviderSelected: (AiProvider) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_provider_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                AiProvider.entries.forEach { provider ->
                    val option = CloudProviderCatalog.findByProvider(provider)
                    DialogOptionRow(
                        title = providerName(provider),
                        subtitle = option?.subtitleRes?.let { stringResource(it) },
                        selected = provider == currentProvider,
                        onClick = { onProviderSelected(provider) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
private fun ModelSelectionDialog(
    title: String,
    icon: ImageVector,
    models: List<AiModel>,
    currentModelId: String?,
    onModelSelected: (AiModel) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState())
            ) {
                models.forEach { model ->
                    DialogOptionRow(
                        title = model.displayName,
                        subtitle = model.id.takeIf { it != model.displayName },
                        selected = model.id == currentModelId,
                        onClick = { onModelSelected(model) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

/**
 * Диалог ввода API ключа.
 */
@Composable
fun ApiKeyInputDialog(
    title: String,
    currentKey: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var apiKey by remember { mutableStateOf(currentKey) }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Key, contentDescription = null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                supportingText = { Text(stringResource(R.string.setup_stored_securely)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = stringResource(R.string.cd_visibility_toggle)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(apiKey.trim()) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

/**
 * Диалог выбора языка.
 */
@Composable
fun LanguageSelectionDialog(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Language, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_language_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    DialogOptionRow(
                        title = stringResource(language.displayNameResId),
                        subtitle = null,
                        selected = language == currentLanguage,
                        onClick = { onLanguageSelected(language) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

/**
 * Строка выбора в диалогах: подсвечивает выбранный вариант, а не только радиокнопку.
 */
@Composable
private fun DialogOptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = AIVoiceNotesMotion.fadeTween(),
        label = "dialog_option_container"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = spacing.medium,
                vertical = spacing.small
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            RadioButton(selected = selected, onClick = null)

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun providerName(provider: AiProvider): String = stringResource(
    when (provider) {
        AiProvider.GEMINI -> R.string.settings_provider_gemini
        AiProvider.OPENAI -> R.string.settings_provider_openai
        AiProvider.GROQ -> R.string.settings_provider_groq
    }
)

private fun providerIcon(providerId: String?): ImageVector = when (providerId) {
    "gemini" -> Icons.Default.AutoAwesome
    "openai" -> Icons.Outlined.Psychology
    "groq" -> Icons.Default.Bolt
    else -> Icons.Default.Cloud
}

private fun themeModeIcon(mode: ThemeMode): ImageVector = when (mode) {
    ThemeMode.SYSTEM -> Icons.Outlined.Smartphone
    ThemeMode.LIGHT -> Icons.Default.LightMode
    ThemeMode.DARK -> Icons.Default.DarkMode
}

private fun themeModeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}
