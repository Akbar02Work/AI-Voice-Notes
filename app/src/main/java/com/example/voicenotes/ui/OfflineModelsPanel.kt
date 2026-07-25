package com.example.voicenotes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.voicenotes.R
import com.example.voicenotes.ai.LocalModelCatalog
import com.example.voicenotes.ai.LocalModelInstallStatus
import com.example.voicenotes.ai.LocalModelState
import com.example.voicenotes.ui.theme.spacing

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun OfflineModelsPanel(
    states: List<LocalModelState>,
    activeModelId: String?,
    onDownload: (String) -> Unit,
    onPause: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    onActivate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        states.forEach { state ->
            OfflineModelCard(
                state = state,
                isActive = state.model.id == activeModelId,
                onDownload = { onDownload(state.model.id) },
                onPause = { onPause(state.model.id) },
                onCancel = { onCancel(state.model.id) },
                onDelete = { onDelete(state.model.id) },
                onActivate = { onActivate(state.model.id) },
                onOpenModelPage = { uriHandler.openUri(state.model.huggingFaceUrl) }
            )
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.padding(spacing.large),
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
            ) {
                TextButton(
                    onClick = { uriHandler.openUri(LocalModelCatalog.MORE_MODELS_URL) }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(R.string.local_models_more_hf),
                        modifier = Modifier.padding(start = spacing.small)
                    )
                }
                Text(
                    text = stringResource(R.string.local_models_more_hf_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun OfflineModelCard(
    state: LocalModelState,
    isActive: Boolean,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit,
    onOpenModelPage: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val model = state.model
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isActive) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (isActive) 2.dp else 1.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(model.nameRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(
                            R.string.local_model_sizes,
                            model.sizeLabel,
                            model.installedSizeLabel
                        ) + " · ONNX · RU",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isActive) {
                    Text(
                        text = stringResource(R.string.local_model_active),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = stringResource(model.descriptionRes),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${stringResource(model.speedRes)} · ${stringResource(model.qualityRes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.local_model_ram, model.minRamMb / 1024),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!state.isCompatible) {
                Text(
                    text = stringResource(
                        R.string.local_model_incompatible,
                        model.minRamMb / 1024
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            ModelStatus(state)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
            ) {
                when (state.status) {
                    LocalModelInstallStatus.NOT_INSTALLED,
                    LocalModelInstallStatus.FAILED -> {
                        Button(onClick = onDownload, enabled = state.isCompatible) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Text(
                                stringResource(R.string.local_model_download),
                                modifier = Modifier.padding(start = spacing.extraSmall)
                            )
                        }
                    }
                    LocalModelInstallStatus.QUEUED,
                    LocalModelInstallStatus.DOWNLOADING -> {
                        OutlinedButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Text(
                                stringResource(R.string.local_model_pause),
                                modifier = Modifier.padding(start = spacing.extraSmall)
                            )
                        }
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Text(
                                stringResource(R.string.local_model_cancel),
                                modifier = Modifier.padding(start = spacing.extraSmall)
                            )
                        }
                    }
                    LocalModelInstallStatus.VERIFYING -> {
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Text(
                                stringResource(R.string.local_model_cancel),
                                modifier = Modifier.padding(start = spacing.extraSmall)
                            )
                        }
                    }
                    LocalModelInstallStatus.PAUSED -> {
                        Button(onClick = onDownload) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(
                                stringResource(R.string.local_model_resume),
                                modifier = Modifier.padding(start = spacing.extraSmall)
                            )
                        }
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Text(
                                stringResource(R.string.local_model_cancel),
                                modifier = Modifier.padding(start = spacing.extraSmall)
                            )
                        }
                    }
                    LocalModelInstallStatus.INSTALLED -> {
                        if (!isActive) {
                            Button(onClick = onActivate) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Text(
                                    stringResource(R.string.local_model_use),
                                    modifier = Modifier.padding(start = spacing.extraSmall)
                                )
                            }
                        }
                        OutlinedButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Text(
                                stringResource(R.string.local_model_delete),
                                modifier = Modifier.padding(start = spacing.extraSmall)
                            )
                        }
                    }
                }

                TextButton(onClick = onOpenModelPage) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null
                    )
                    Text(
                        stringResource(R.string.local_model_hf_page),
                        modifier = Modifier.padding(start = spacing.extraSmall)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelStatus(state: LocalModelState) {
    val spacing = MaterialTheme.spacing
    val statusText = when (state.status) {
        LocalModelInstallStatus.NOT_INSTALLED -> null
        LocalModelInstallStatus.QUEUED -> stringResource(R.string.local_model_queued)
        LocalModelInstallStatus.DOWNLOADING ->
            stringResource(R.string.local_model_downloading, state.progressPercent)
        LocalModelInstallStatus.VERIFYING ->
            stringResource(R.string.local_model_verifying)
        LocalModelInstallStatus.PAUSED ->
            stringResource(R.string.local_model_paused, state.progressPercent)
        LocalModelInstallStatus.INSTALLED -> stringResource(R.string.local_model_installed)
        LocalModelInstallStatus.FAILED ->
            stringResource(R.string.local_model_failed, state.error.orEmpty())
    }
    if (statusText != null) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = if (state.status == LocalModelInstallStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            if (
                state.status == LocalModelInstallStatus.DOWNLOADING ||
                state.status == LocalModelInstallStatus.VERIFYING ||
                state.status == LocalModelInstallStatus.PAUSED
            ) {
                LinearProgressIndicator(
                    progress = { state.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
