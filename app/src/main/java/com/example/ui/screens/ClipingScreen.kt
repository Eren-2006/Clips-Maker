package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CaptionLanguage
import com.example.model.ClipDownloadState
import com.example.model.ClipErrorKind
import com.example.model.ClipJobUiState
import com.example.model.GeneratedClip
import com.example.model.ProcessingState
import com.example.ui.components.ClipPlayerCard
import com.example.ui.components.PipelineProgressCard
import com.example.ui.components.formatLanguageBadge
import com.example.ui.theme.CoralError
import com.example.ui.theme.CoralErrorContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.KineticYellow
import com.example.ui.theme.KineticYellowContainer
import com.example.ui.theme.OnCoralErrorContainer
import com.example.ui.theme.StudioCanvas
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClipingScreen(
    urlInput: String,
    selectedLanguage: CaptionLanguage,
    uiState: ClipJobUiState,
    processingState: ProcessingState,
    activePlayingClipId: String?,
    onUrlChanged: (String) -> Unit,
    onLanguageSelected: (CaptionLanguage) -> Unit,
    onClearUrl: () -> Unit,
    onGenerateClick: () -> Unit,
    onResetToIdle: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onTogglePlayClip: (String) -> Unit,
    onDownloadClip: (GeneratedClip) -> Unit,
    onDownloadAllZip: () -> Unit,
    onShareClip: (GeneratedClip) -> Unit,
    onShowToast: (String) -> Unit,
    onOpenFullscreen: (GeneratedClip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val copiedToast = stringResource(R.string.msg_copied_clipboard)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(StudioCanvas)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            KineticYellow.copy(alpha = 0.09f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = size.width * 0.85f,
                    )
                )
            }
    ) {
        val columnCount = when {
            maxWidth >= 900.dp -> 3
            maxWidth >= 600.dp -> 2
            else -> 1
        }
        val horizontalPadding = if (maxWidth >= 600.dp) 24.dp else 16.dp

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            modifier = Modifier
                .fillMaxSize()
                .testTag("cliping_screen_grid"),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 12.dp,
                bottom = 32.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top Studio Header & VideoInputScreen input card
            item(span = { GridItemSpan(columnCount) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    VideoInputHeader(onOpenServerSettings = onOpenServerSettings)

                    VideoInputCard(
                        urlInput = urlInput,
                        isProcessing = uiState is ClipJobUiState.Processing,
                        selectedLanguage = selectedLanguage,
                        onLanguageSelected = onLanguageSelected,
                        onUrlChanged = onUrlChanged,
                        onClearUrl = onClearUrl,
                        onProcessClick = onGenerateClick,
                        onShowToast = onShowToast,
                    )
                }
            }

            // State-Dependent Body
            when (uiState) {
                ClipJobUiState.Idle -> {
                    item(span = { GridItemSpan(columnCount) }) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("idle_capabilities_row"),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CapabilityPill(
                                icon = Icons.Filled.MovieFilter,
                                text = "9:16 Face-Tracked Crop",
                            )
                            CapabilityPill(
                                icon = Icons.Filled.Subtitles,
                                text = "EN • HI • BN Word Captions",
                            )
                            CapabilityPill(
                                icon = Icons.Filled.Tune,
                                text = "Loudness Normalized MP4",
                            )
                        }
                    }
                }

                is ClipJobUiState.Processing -> {
                    item(span = { GridItemSpan(columnCount) }) {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            PipelineProgressCard(state = processingState)
                        }
                    }
                }

                is ClipJobUiState.Error -> {
                    item(span = { GridItemSpan(columnCount) }) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pipeline_error_card"),
                            shape = MaterialTheme.shapes.large,
                            color = CoralErrorContainer,
                            border = BorderStroke(1.dp, CoralError.copy(alpha = 0.55f)),
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = "Error",
                                        tint = CoralError,
                                    )
                                    Text(
                                        text = when (uiState.kind) {
                                            ClipErrorKind.INVALID_URL -> "Invalid YouTube URL"
                                            ClipErrorKind.PRIVATE_VIDEO -> "Video Unavailable"
                                            ClipErrorKind.VIDEO_TOO_LONG -> "Video Exceeds 2-Hour Limit"
                                            ClipErrorKind.SERVER_UNREACHABLE -> "Backend Server Offline"
                                            ClipErrorKind.AI_FAILURE -> "AI Moment Selection Failed"
                                            ClipErrorKind.PIPELINE_FAILURE -> "Processing Failed"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = CoralError,
                                    )
                                }

                                Text(
                                    text = uiState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnCoralErrorContainer,
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Button(
                                        onClick = onGenerateClick,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("retry_generate_button"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CoralError,
                                            contentColor = StudioCanvas,
                                        ),
                                    ) {
                                        Text(stringResource(R.string.action_retry))
                                    }

                                    if (uiState.kind == ClipErrorKind.SERVER_UNREACHABLE) {
                                        OutlinedButton(
                                            onClick = onOpenServerSettings,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("error_server_settings_button"),
                                            border = BorderStroke(1.dp, CoralError.copy(alpha = 0.6f)),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.action_server_settings),
                                                color = OnCoralErrorContainer,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                is ClipJobUiState.Completed -> {
                    item(span = { GridItemSpan(columnCount) }) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("results_summary_banner"),
                            shape = MaterialTheme.shapes.large,
                            color = StudioSurface,
                            border = BorderStroke(1.dp, StudioHairline),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = uiState.videoTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "${uiState.clips.size} ready-to-post clips",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = EmeraldSuccess,
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = ElectricCyanContainer,
                                            ) {
                                                Text(
                                                    text = formatLanguageBadge(uiState.detectedLanguage),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = ElectricCyan,
                                                    modifier = Modifier.padding(
                                                        horizontal = 8.dp,
                                                        vertical = 3.dp,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (uiState.zipDownloadUrl != null) {
                                        val isZipLoading = uiState.zipDownloadState is ClipDownloadState.Downloading
                                        val isZipSaved = uiState.zipDownloadState is ClipDownloadState.Saved
                                        Button(
                                            onClick = onDownloadAllZip,
                                            enabled = !isZipLoading,
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                                .testTag("download_all_zip_button"),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isZipSaved) EmeraldContainer else KineticYellow,
                                                contentColor = if (isZipSaved) EmeraldSuccess else StudioCanvas,
                                            ),
                                        ) {
                                            if (isZipLoading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = StudioCanvas,
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = if (isZipSaved) Icons.Filled.Check else Icons.Filled.Archive,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isZipSaved) "ZIP Saved" else stringResource(R.string.action_download_all_zip)
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = onResetToIdle,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("new_video_button"),
                                        border = BorderStroke(1.dp, StudioHairline),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.action_new_video),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(
                        items = uiState.clips,
                        key = { it.id },
                    ) { clip ->
                        ClipPlayerCard(
                            clip = clip,
                            isActivePlayer = activePlayingClipId == clip.id,
                            downloadState = uiState.downloadStates[clip.id] ?: ClipDownloadState.Idle,
                            onTogglePlay = { onTogglePlayClip(clip.id) },
                            onDownloadClick = { onDownloadClip(clip) },
                            onCopyCaptionClick = {
                                clipboardManager.setText(AnnotatedString(clip.formattedCopyText()))
                                onShowToast(copiedToast)
                            },
                            onShareClick = { onShareClip(clip) },
                            onExpandFullscreen = { onOpenFullscreen(clip) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityPill(
    icon: ImageVector,
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioHairline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KineticYellow,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
        }
    }
}
