package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.YouTubeUrlValidator
import com.example.model.CaptionLanguage
import com.example.ui.theme.KineticYellow
import com.example.ui.theme.KineticYellowContainer
import com.example.ui.theme.StudioCanvas
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoInputScreen(
    urlInput: String,
    isProcessing: Boolean,
    selectedLanguage: CaptionLanguage = CaptionLanguage.AUTO,
    onLanguageSelected: (CaptionLanguage) -> Unit = {},
    onUrlChanged: (String) -> Unit,
    onClearUrl: () -> Unit,
    onProcessClick: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onShowToast: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
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
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("video_input_screen"),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            VideoInputHeader(onOpenServerSettings = onOpenServerSettings)

            VideoInputCard(
                urlInput = urlInput,
                isProcessing = isProcessing,
                selectedLanguage = selectedLanguage,
                onLanguageSelected = onLanguageSelected,
                onUrlChanged = onUrlChanged,
                onClearUrl = onClearUrl,
                onProcessClick = onProcessClick,
                onShowToast = onShowToast,
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("idle_capabilities_row"),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VideoCapabilityPill(
                    icon = Icons.Filled.MovieFilter,
                    text = "9:16 Face-Tracked Crop",
                )
                VideoCapabilityPill(
                    icon = Icons.Filled.Subtitles,
                    text = "EN • HI • BN Word Captions",
                )
                VideoCapabilityPill(
                    icon = Icons.Filled.Tune,
                    text = "Loudness Normalized MP4",
                )
            }
        }
    }
}

@Composable
fun VideoInputHeader(
    onOpenServerSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = KineticYellow,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MovieFilter,
                        contentDescription = "ClipMaster",
                        tint = StudioCanvas,
                        modifier = Modifier
                            .padding(7.dp)
                            .size(20.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = StudioSurfaceElevated,
                    border = BorderStroke(1.dp, StudioHairline),
                ) {
                    Text(
                        text = stringResource(R.string.route_badge_cliping),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = KineticYellow,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }

            IconButton(
                onClick = onOpenServerSettings,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("open_server_settings_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.action_server_settings),
                    tint = TextSecondary,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.studio_headline),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.studio_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoInputCard(
    urlInput: String,
    isProcessing: Boolean,
    selectedLanguage: CaptionLanguage = CaptionLanguage.AUTO,
    onLanguageSelected: (CaptionLanguage) -> Unit = {},
    onUrlChanged: (String) -> Unit,
    onClearUrl: () -> Unit,
    onProcessClick: () -> Unit,
    onShowToast: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var languageMenuExpanded by remember { mutableStateOf(false) }

    fun pasteFromClipboard() {
        val raw = clipboardManager.getText()?.text.orEmpty()
        if (raw.isBlank()) {
            onShowToast("Clipboard is empty. Copy a YouTube link first.")
            return
        }
        val extracted = YouTubeUrlValidator.extractFirstUrl(raw)
        onUrlChanged(extracted)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioHairline),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = onUrlChanged,
                label = { Text(stringResource(R.string.youtube_url_label)) },
                placeholder = {
                    Text(
                        text = stringResource(R.string.youtube_url_placeholder),
                        color = TextMuted,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = KineticYellow,
                    )
                },
                trailingIcon = {
                    if (urlInput.isNotEmpty()) {
                        IconButton(
                            onClick = onClearUrl,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_url_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.action_clear),
                                tint = TextSecondary,
                            )
                        }
                    } else {
                        IconButton(
                            onClick = ::pasteFromClipboard,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("paste_url_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = stringResource(R.string.action_paste),
                                tint = TextSecondary,
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        keyboardController?.hide()
                        onProcessClick()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("youtube_url_input"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KineticYellow,
                    unfocusedBorderColor = StudioHairline,
                    focusedContainerColor = StudioSurfaceElevated,
                    unfocusedContainerColor = StudioSurfaceElevated,
                ),
            )

            ExposedDropdownMenuBox(
                expanded = languageMenuExpanded && !isProcessing,
                onExpandedChange = {
                    if (!isProcessing) {
                        languageMenuExpanded = !languageMenuExpanded
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = "${selectedLanguage.displayLabel}  •  ${selectedLanguage.scriptHint}",
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isProcessing,
                    singleLine = true,
                    label = { Text(stringResource(R.string.caption_language_label)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Translate,
                            contentDescription = null,
                            tint = KineticYellow,
                        )
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = languageMenuExpanded && !isProcessing
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !isProcessing)
                        .testTag("caption_language_dropdown"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KineticYellow,
                        unfocusedBorderColor = StudioHairline,
                        disabledBorderColor = StudioHairline.copy(alpha = 0.6f),
                        focusedContainerColor = StudioSurfaceElevated,
                        unfocusedContainerColor = StudioSurfaceElevated,
                        disabledContainerColor = StudioSurfaceElevated.copy(alpha = 0.6f),
                    ),
                )

                ExposedDropdownMenu(
                    expanded = languageMenuExpanded && !isProcessing,
                    onDismissRequest = { languageMenuExpanded = false },
                    modifier = Modifier.background(StudioSurfaceElevated),
                ) {
                    CaptionLanguage.entries.forEach { option ->
                        val isSelected = option == selectedLanguage
                        DropdownMenuItem(
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = when (option) {
                                            CaptionLanguage.AUTO -> stringResource(R.string.caption_language_auto)
                                            CaptionLanguage.ENGLISH -> stringResource(R.string.caption_language_english)
                                            CaptionLanguage.HINDI -> stringResource(R.string.caption_language_hindi)
                                            CaptionLanguage.BENGALI -> stringResource(R.string.caption_language_bengali)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) KineticYellow else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = when (option) {
                                            CaptionLanguage.AUTO -> stringResource(R.string.caption_language_auto_hint)
                                            CaptionLanguage.ENGLISH -> stringResource(R.string.caption_language_english_hint)
                                            CaptionLanguage.HINDI -> stringResource(R.string.caption_language_hindi_hint)
                                            CaptionLanguage.BENGALI -> stringResource(R.string.caption_language_bengali_hint)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                    )
                                }
                            },
                            trailingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = KineticYellow,
                                    )
                                }
                            } else {
                                null
                            },
                            onClick = {
                                onLanguageSelected(option)
                                languageMenuExpanded = false
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("caption_language_option_${option.code}"),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = ::pasteFromClipboard,
                    enabled = !isProcessing,
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("paste_url_action_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, StudioHairline),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_paste),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Button(
                    onClick = {
                        keyboardController?.hide()
                        onProcessClick()
                    },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("process_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KineticYellow,
                        contentColor = StudioCanvas,
                        disabledContainerColor = KineticYellowContainer,
                        disabledContentColor = KineticYellow,
                    ),
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = KineticYellow,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Processing...")
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_process),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.label_max_duration_hint),
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun VideoCapabilityPill(
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
