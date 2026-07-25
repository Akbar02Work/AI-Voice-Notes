package com.example.voicenotes.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.voicenotes.R
import com.example.voicenotes.ai.CloudProviderCatalog
import com.example.voicenotes.ai.CloudProviderOption
import com.example.voicenotes.ai.AvailableAiModels
import com.example.voicenotes.ai.AiModel
import com.example.voicenotes.ai.InferenceMode
import com.example.voicenotes.ai.LocalModelState
import com.example.voicenotes.ui.theme.VoiceNotesMotion
import com.example.voicenotes.ui.theme.VoiceNotesPillShape
import com.example.voicenotes.ui.theme.spacing

@Composable
fun SetupScreen(
    inferenceMode: InferenceMode,
    selectedProviderId: String,
    selectedLocalModelId: String?,
    localModelStates: List<LocalModelState>,
    apiKey: String,
    availableModels: AvailableAiModels?,
    selectedTranscriptionModel: String?,
    selectedSummaryModel: String?,
    isCheckingKey: Boolean,
    modelError: String?,
    isApiKeyVisible: Boolean,
    isSaving: Boolean,
    onInferenceModeSelected: (InferenceMode) -> Unit,
    onProviderSelected: (CloudProviderOption) -> Unit,
    onLocalModelSelected: (String) -> Unit,
    onLocalModelDownload: (String) -> Unit,
    onLocalModelPause: (String) -> Unit,
    onLocalModelCancel: (String) -> Unit,
    onLocalModelDelete: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onCheckApiKey: () -> Unit,
    onTranscriptionModelSelected: (AiModel) -> Unit,
    onSummaryModelSelected: (AiModel) -> Unit,
    onApiKeyVisibilityToggle: () -> Unit,
    onGetStartedClick: () -> Unit,
    onSkipClick: () -> Unit,
    onFindApiKeyClick: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val selectedProvider = CloudProviderCatalog.findById(selectedProviderId)
    val canSubmitCloud = selectedProvider != null &&
        apiKey.isNotBlank() &&
        availableModels != null &&
        selectedTranscriptionModel != null &&
        selectedSummaryModel != null &&
        !isSaving
    val canSubmitLocal = localModelStates.any {
        it.model.id == selectedLocalModelId && it.isInstalled
    } && !isSaving
    val canSubmit = when (inferenceMode) {
        InferenceMode.CLOUD -> canSubmitCloud
        InferenceMode.LOCAL -> canSubmitLocal
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            SetupBottomBar(
                inferenceMode = inferenceMode,
                canSubmit = canSubmit,
                isSaving = isSaving,
                onGetStartedClick = onGetStartedClick,
                onSkipClick = onSkipClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(spacing.large)
        ) {
            SetupHeader(inferenceMode = inferenceMode)

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                InferenceMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = mode == inferenceMode,
                        onClick = { onInferenceModeSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = InferenceMode.entries.size
                        ),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = mode == inferenceMode) {
                                Icon(
                                    imageVector = when (mode) {
                                        InferenceMode.CLOUD -> Icons.Default.Cloud
                                        InferenceMode.LOCAL -> Icons.Default.PhoneAndroid
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(
                                    when (mode) {
                                        InferenceMode.CLOUD -> R.string.setup_mode_cloud
                                        InferenceMode.LOCAL -> R.string.setup_mode_local
                                    }
                                )
                            )
                        }
                    )
                }
            }

            AnimatedContent(
                targetState = inferenceMode,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val offset = if (forward) 1 else -1
                    (
                        fadeIn(animationSpec = VoiceNotesMotion.fadeTween()) +
                            slideInVertically(animationSpec = VoiceNotesMotion.navTween()) {
                                offset * it / 12
                            }
                        ) togetherWith fadeOut(animationSpec = VoiceNotesMotion.fadeTween())
                },
                label = "setup_mode_content"
            ) { mode ->
                when (mode) {
                    InferenceMode.CLOUD -> CloudSetupSection(
                        selectedProviderId = selectedProviderId,
                        apiKey = apiKey,
                        availableModels = availableModels,
                        selectedTranscriptionModel = selectedTranscriptionModel,
                        selectedSummaryModel = selectedSummaryModel,
                        isCheckingKey = isCheckingKey,
                        modelError = modelError,
                        isApiKeyVisible = isApiKeyVisible,
                        onProviderSelected = onProviderSelected,
                        onApiKeyChanged = onApiKeyChanged,
                        onCheckApiKey = onCheckApiKey,
                        onTranscriptionModelSelected = onTranscriptionModelSelected,
                        onSummaryModelSelected = onSummaryModelSelected,
                        onApiKeyVisibilityToggle = onApiKeyVisibilityToggle,
                        onFindApiKeyClick = onFindApiKeyClick,
                        onSubmitFromKeyboard = { if (canSubmitCloud) onGetStartedClick() }
                    )

                    InferenceMode.LOCAL -> LocalSetupSection(
                        selectedLocalModelId = selectedLocalModelId,
                        states = localModelStates,
                        onLocalModelSelected = onLocalModelSelected,
                        onLocalModelDownload = onLocalModelDownload,
                        onLocalModelPause = onLocalModelPause,
                        onLocalModelCancel = onLocalModelCancel,
                        onLocalModelDelete = onLocalModelDelete
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.small))
        }
    }
}

@Composable
private fun SetupHeader(inferenceMode: InferenceMode) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = inferenceMode,
                    transitionSpec = {
                        fadeIn(animationSpec = VoiceNotesMotion.fadeTween()) togetherWith
                            fadeOut(animationSpec = VoiceNotesMotion.fadeTween())
                    },
                    label = "setup_header_icon"
                ) { mode ->
                    Icon(
                        imageVector = when (mode) {
                            InferenceMode.CLOUD -> Icons.Default.Cloud
                            InferenceMode.LOCAL -> Icons.Default.PhoneAndroid
                        },
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = stringResource(R.string.setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CloudSetupSection(
    selectedProviderId: String,
    apiKey: String,
    availableModels: AvailableAiModels?,
    selectedTranscriptionModel: String?,
    selectedSummaryModel: String?,
    isCheckingKey: Boolean,
    modelError: String?,
    isApiKeyVisible: Boolean,
    onProviderSelected: (CloudProviderOption) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onCheckApiKey: () -> Unit,
    onTranscriptionModelSelected: (AiModel) -> Unit,
    onSummaryModelSelected: (AiModel) -> Unit,
    onApiKeyVisibilityToggle: () -> Unit,
    onFindApiKeyClick: () -> Unit,
    onSubmitFromKeyboard: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val selected = CloudProviderCatalog.findById(selectedProviderId)
    val available = CloudProviderCatalog.options

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        SectionLabel(text = stringResource(R.string.setup_select_provider))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            available.forEach { option ->
                OptionRow(
                    icon = providerIcon(option.id),
                    title = stringResource(option.titleRes),
                    selected = option.id == selectedProviderId,
                    onClick = { onProviderSelected(option) }
                ) {
                    Text(
                        text = stringResource(option.subtitleRes),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selected != null,
            enter = fadeIn(animationSpec = VoiceNotesMotion.fadeTween()) +
                expandVertically(animationSpec = VoiceNotesMotion.navTween()),
            exit = fadeOut(animationSpec = VoiceNotesMotion.fadeTween()) +
                shrinkVertically(animationSpec = VoiceNotesMotion.navTween())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
            ) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.setup_api_key_placeholder)) },
                    supportingText = { Text(stringResource(R.string.setup_stored_securely)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = stringResource(R.string.cd_setup_key_icon)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = onApiKeyVisibilityToggle) {
                            Icon(
                                imageVector = if (isApiKeyVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = stringResource(
                                    if (isApiKeyVisible) {
                                        R.string.cd_setup_hide_key
                                    } else {
                                        R.string.cd_setup_show_key
                                    }
                                )
                            )
                        }
                    },
                    visualTransformation = if (isApiKeyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmitFromKeyboard() })
                )

                TextButton(
                    onClick = onFindApiKeyClick,
                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.setup_find_api_key))
                }

                Button(
                    onClick = onCheckApiKey,
                    enabled = apiKey.isNotBlank() && !isCheckingKey,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCheckingKey) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.setup_check_key))
                    }
                }

                modelError?.let {
                    Text(
                        text = stringResource(R.string.setup_model_error, it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                availableModels?.let { models ->
                    Text(
                        text = stringResource(R.string.setup_key_valid),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )

                    SectionLabel(
                        text = stringResource(R.string.setup_select_transcription_model),
                        modifier = Modifier.padding(top = spacing.small)
                    )
                    models.transcription.forEach { model ->
                        OptionRow(
                            icon = Icons.Default.Bolt,
                            title = model.displayName,
                            selected = model.id == selectedTranscriptionModel,
                            onClick = { onTranscriptionModelSelected(model) }
                        ) {
                            if (model.displayName != model.id) {
                                Text(model.id, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    SectionLabel(
                        text = stringResource(R.string.setup_select_summary_model),
                        modifier = Modifier.padding(top = spacing.small)
                    )
                    models.summarization.forEach { model ->
                        OptionRow(
                            icon = Icons.Default.AutoAwesome,
                            title = model.displayName,
                            selected = model.id == selectedSummaryModel,
                            onClick = { onSummaryModelSelected(model) }
                        ) {
                            if (model.displayName != model.id) {
                                Text(model.id, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalSetupSection(
    selectedLocalModelId: String?,
    states: List<LocalModelState>,
    onLocalModelSelected: (String) -> Unit,
    onLocalModelDownload: (String) -> Unit,
    onLocalModelPause: (String) -> Unit,
    onLocalModelCancel: (String) -> Unit,
    onLocalModelDelete: (String) -> Unit
) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(spacing.large),
                horizontalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.setup_local_download_unavailable),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        SectionLabel(
            text = stringResource(R.string.setup_select_local_model),
            modifier = Modifier.padding(top = spacing.small)
        )

        OfflineModelsPanel(
            states = states,
            activeModelId = selectedLocalModelId,
            onDownload = onLocalModelDownload,
            onPause = onLocalModelPause,
            onCancel = onLocalModelCancel,
            onDelete = onLocalModelDelete,
            onActivate = onLocalModelSelected
        )
    }
}

/**
 * Selectable list row shared by cloud providers and local models.
 * Selection is animated so the choice reads as a state change, not a repaint.
 */
@Composable
private fun OptionRow(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    val spacing = MaterialTheme.spacing
    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerLowest
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = VoiceNotesMotion.fadeTween(),
        label = "option_container"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected && enabled -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = VoiceNotesMotion.fadeTween(),
        label = "option_border"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected && enabled) 2.dp else 1.dp,
        animationSpec = VoiceNotesMotion.snappySpring(),
        label = "option_border_width"
    )
    val contentAlpha = if (enabled) 1f else 0.38f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = if (selected && enabled) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
        },
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(spacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            Surface(
                shape = VoiceNotesPillShape,
                color = if (selected && enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (selected && enabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                }
            ) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (content != null) {
                    CompositionLocalProvider(
                        LocalContentColor provides if (selected && enabled) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                        },
                        content = { content() }
                    )
                }
            }

            trailing?.invoke()

            if (selected && enabled) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun SetupBottomBar(
    inferenceMode: InferenceMode,
    canSubmit: Boolean,
    isSaving: Boolean,
    onGetStartedClick: () -> Unit,
    onSkipClick: () -> Unit
) {
    val spacing = MaterialTheme.spacing

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Union keeps the bar just above the keyboard without stacking nav-bar inset
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(
                    horizontal = spacing.screenHorizontal,
                    vertical = spacing.medium
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
        ) {
            Button(
                onClick = onGetStartedClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = canSubmit,
                shape = VoiceNotesPillShape
            ) {
                AnimatedContent(
                    targetState = isSaving,
                    transitionSpec = {
                        fadeIn(animationSpec = VoiceNotesMotion.fadeTween()) togetherWith
                            fadeOut(animationSpec = VoiceNotesMotion.fadeTween())
                    },
                    label = "setup_cta_content"
                ) { saving ->
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(
                                    when (inferenceMode) {
                                        InferenceMode.CLOUD -> R.string.setup_get_started
                                        InferenceMode.LOCAL -> R.string.setup_continue_local
                                    }
                                ),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.cd_setup_arrow_icon),
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = onSkipClick,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = VoiceNotesPillShape
            ) {
                Text(
                    text = stringResource(R.string.setup_skip),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

private fun providerIcon(providerId: String): ImageVector = when (providerId) {
    "gemini" -> Icons.Default.AutoAwesome
    "openai" -> Icons.Outlined.Psychology
    "groq" -> Icons.Default.Bolt
    else -> Icons.Default.Cloud
}
