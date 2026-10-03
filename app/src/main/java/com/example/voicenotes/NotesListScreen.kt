package com.example.voicenotes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.voicenotes.data.NoteStatus
import com.example.voicenotes.ui.theme.AIVoiceNotesMotion
import com.example.voicenotes.ui.theme.AIVoiceNotesPillShape
import com.example.voicenotes.ui.theme.spacing
import com.example.voicenotes.util.ErrorHandler
import java.io.File

@Composable
fun NoteListCard(
    note: NoteUi,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val containerColor by animateColorAsState(
        targetValue = if (note.isPinned) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = AIVoiceNotesMotion.fadeTween(),
        label = "note_card_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (note.isPinned) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = AIVoiceNotesMotion.fadeTween(),
        label = "note_card_fg"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (note.isPinned) 1.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                Surface(
                    shape = AIVoiceNotesPillShape,
                    color = if (note.isPinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (note.isPinned) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ) {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (note.isPinned) {
                                Icons.Filled.PushPin
                            } else {
                                Icons.Outlined.GraphicEq
                            },
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = note.formattedDate,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val preview = note.summary.ifBlank { note.previewText }
            if (preview.isNotBlank() && note.status == NoteStatus.SYNCED) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(
                visible = note.status != NoteStatus.SYNCED,
                enter = fadeIn(AIVoiceNotesMotion.fadeTween()) +
                    expandVertically(AIVoiceNotesMotion.navTween()),
                exit = fadeOut(AIVoiceNotesMotion.fadeTween()) +
                    shrinkVertically(AIVoiceNotesMotion.navTween())
            ) {
                NoteStatusBanner(
                    status = note.status,
                    detail = note.summary.takeIf { it.isNotBlank() },
                    onRetry = onRetry
                )
            }
        }
    }
}

@Composable
private fun NoteStatusBanner(
    status: NoteStatus,
    detail: String?,
    onRetry: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val context = LocalContext.current

    val (container, onContainer, icon, labelRes, showRetry) = when (status) {
        NoteStatus.PROCESSING -> StatusStyle(
            container = MaterialTheme.colorScheme.primaryContainer,
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = null,
            labelRes = R.string.note_status_processing,
            showRetry = false
        )
        NoteStatus.DRAFT -> StatusStyle(
            container = MaterialTheme.colorScheme.tertiaryContainer,
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = Icons.Default.CloudOff,
            labelRes = R.string.note_status_draft,
            showRetry = true
        )
        NoteStatus.FAILED -> StatusStyle(
            container = MaterialTheme.colorScheme.errorContainer,
            onContainer = MaterialTheme.colorScheme.onErrorContainer,
            icon = Icons.Default.ErrorOutline,
            labelRes = R.string.note_status_failed,
            showRetry = true
        )
        NoteStatus.SYNCED -> return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = onContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                if (status == NoteStatus.PROCESSING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = onContainer,
                        trackColor = onContainer.copy(alpha = 0.2f)
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!detail.isNullOrBlank() && status != NoteStatus.PROCESSING) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = onContainer.copy(alpha = 0.85f)
                )
            }

            if (showRetry) {
                FilledTonalButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = context.getString(R.string.cd_retry_processing)
                        },
                    shape = AIVoiceNotesPillShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = onContainer
                    ),
                    contentPadding = PaddingValues(
                        horizontal = spacing.large,
                        vertical = spacing.medium
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = stringResource(R.string.note_action_retry),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

private data class StatusStyle(
    val container: Color,
    val onContainer: Color,
    val icon: ImageVector?,
    val labelRes: Int,
    val showRetry: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableNoteCard(
    note: NoteUi,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onPinToggle: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val cardShape = MaterialTheme.shapes.large
    val latestOnDelete by rememberUpdatedState(onDelete)
    val latestOnPin by rememberUpdatedState(onPinToggle)
    val haptic = LocalHapticFeedback.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    latestOnDelete()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    latestOnPin()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.28f }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.targetValue
            val showDelete = direction == SwipeToDismissBoxValue.EndToStart
            val showPin = direction == SwipeToDismissBoxValue.StartToEnd

            val color by animateColorAsState(
                targetValue = when {
                    showDelete -> MaterialTheme.colorScheme.error
                    showPin -> MaterialTheme.colorScheme.primary
                    else -> Color.Transparent
                },
                animationSpec = AIVoiceNotesMotion.fadeTween(),
                label = "swipe_bg"
            )
            val contentColor by animateColorAsState(
                targetValue = when {
                    showDelete -> MaterialTheme.colorScheme.onError
                    showPin -> MaterialTheme.colorScheme.onPrimary
                    else -> Color.Transparent
                },
                animationSpec = AIVoiceNotesMotion.fadeTween(),
                label = "swipe_fg"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
                    .background(color),
                contentAlignment = when {
                    showDelete -> Alignment.CenterEnd
                    showPin -> Alignment.CenterStart
                    else -> Alignment.Center
                }
            ) {
                if (showDelete || showPin) {
                    Row(
                        modifier = Modifier.padding(horizontal = spacing.extraLarge),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        if (showPin) {
                            Icon(
                                imageVector = if (note.isPinned) {
                                    Icons.Outlined.PushPin
                                } else {
                                    Icons.Filled.PushPin
                                },
                                contentDescription = stringResource(
                                    if (note.isPinned) R.string.cd_unpin_note else R.string.cd_pin_note
                                ),
                                tint = contentColor
                            )
                            Text(
                                text = stringResource(
                                    if (note.isPinned) R.string.notes_list_unpin else R.string.notes_list_pin
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = contentColor
                            )
                        }
                        if (showDelete) {
                            Text(
                                text = stringResource(R.string.notes_list_delete),
                                style = MaterialTheme.typography.labelLarge,
                                color = contentColor
                            )
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.cd_delete_note),
                                tint = contentColor
                            )
                        }
                    }
                }
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.extraSmall)
    ) {
        NoteListCard(
            note = note,
            onClick = onClick,
            onRetry = onRetry
        )
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.enormous),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.large)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(
                    modifier = Modifier.size(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.notes_list_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.notes_list_empty_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RecordFab(
    isRecording: Boolean,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AIVoiceNotesMotion.longDurationMs,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab_scale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isRecording) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = AIVoiceNotesMotion.fadeTween(),
        label = "fab_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isRecording) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = AIVoiceNotesMotion.fadeTween(),
        label = "fab_fg"
    )

    AnimatedContent(
        targetState = isRecording || expanded,
        transitionSpec = {
            (fadeIn(AIVoiceNotesMotion.fadeTween()) +
                scaleIn(AIVoiceNotesMotion.expressiveSpring(), initialScale = 0.92f)) togetherWith
                (fadeOut(AIVoiceNotesMotion.fadeTween()) + scaleOut(targetScale = 0.92f))
        },
        label = "fab_mode"
    ) { useExtended ->
        if (useExtended) {
            ExtendedFloatingActionButton(
                onClick = onClick,
                modifier = Modifier
                    .height(80.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    },
                shape = AIVoiceNotesPillShape,
                containerColor = containerColor,
                contentColor = contentColor,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                ),
                icon = {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = stringResource(
                            if (isRecording) {
                                R.string.cd_notes_list_fab_recording
                            } else {
                                R.string.cd_notes_list_fab_idle
                            }
                        ),
                        modifier = Modifier.size(32.dp)
                    )
                },
                text = {
                    Text(
                        text = stringResource(
                            if (isRecording) R.string.notes_list_fab_stop
                            else R.string.notes_list_fab_record
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            )
        } else {
            LargeFloatingActionButton(
                onClick = onClick,
                modifier = Modifier.graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                },
                shape = MaterialTheme.shapes.extraLarge,
                containerColor = containerColor,
                contentColor = contentColor,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringResource(R.string.cd_notes_list_fab_idle),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    viewModel: NotesViewModel,
    recordingsDir: File,
    onNoteClick: (Long) -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val spacing = MaterialTheme.spacing

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)

    val fabExpanded by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset < 40
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            val message = ErrorHandler.getLocalizedMessage(context, error)
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording(context, recordingsDir)
        }
    }

    val haptic = LocalHapticFeedback.current

    fun onRecordClick() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        if (uiState.isRecording) {
            viewModel.stopRecording()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                viewModel.startRecording(context, recordingsDir)
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.notes_list_title),
                        style = MaterialTheme.typography.headlineLarge
                    )
                },
                actions = {
                    FilledIconButton(
                        onClick = onSettingsClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.cd_settings_button)
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            RecordFab(
                isRecording = uiState.isRecording,
                expanded = fabExpanded || uiState.isRecording,
                onClick = { onRecordClick() }
            )
        }
    ) { paddingValues ->
        if (notes.isEmpty()) {
            EmptyState(modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + spacing.small,
                    bottom = paddingValues.calculateBottomPadding() + spacing.enormous + spacing.huge,
                    start = 0.dp,
                    end = 0.dp
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = notes,
                    key = { it.id }
                ) { note ->
                    SwipeableNoteCard(
                        note = note,
                        onClick = { onNoteClick(note.id) },
                        onDelete = { viewModel.deleteNote(note.id) },
                        onPinToggle = { viewModel.togglePin(note.id, note.isPinned) },
                        onRetry = { viewModel.retryNote(note) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = AIVoiceNotesMotion.fadeTween(),
                            fadeOutSpec = AIVoiceNotesMotion.fadeTween(),
                            placementSpec = AIVoiceNotesMotion.expressiveSpring()
                        )
                    )
                }
            }
        }
    }
}
