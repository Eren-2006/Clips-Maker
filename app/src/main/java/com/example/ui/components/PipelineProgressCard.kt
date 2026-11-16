package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.PipelineStep
import com.example.model.ProcessingState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.KineticYellow
import com.example.ui.theme.KineticYellowContainer
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PipelineProgressCard(
    state: ProcessingState,
    modifier: Modifier = Modifier,
) {
    val videoTitle = when (state) {
        is ProcessingState.Downloading -> state.videoTitle
        is ProcessingState.Transcribing -> state.videoTitle
        is ProcessingState.FindingMoments -> state.videoTitle
        is ProcessingState.Editing -> state.videoTitle
        is ProcessingState.Done -> state.videoTitle
        else -> null
    }
    val detectedLanguage = when (state) {
        is ProcessingState.Transcribing -> state.detectedLanguage
        is ProcessingState.FindingMoments -> state.detectedLanguage
        is ProcessingState.Editing -> state.detectedLanguage
        is ProcessingState.Done -> state.detectedLanguage
        else -> null
    }
    val queuePosition = (state as? ProcessingState.Downloading)?.queuePosition ?: 0
    val statusMessage = when (state) {
        is ProcessingState.Downloading -> state.message
        is ProcessingState.Transcribing -> state.message
        is ProcessingState.FindingMoments -> state.message
        is ProcessingState.Editing -> state.message
        is ProcessingState.Done -> state.message
        is ProcessingState.Error -> state.message
        ProcessingState.Idle -> "Ready"
    }
    val progressPercent = (state.progress * 100f).roundToInt().coerceIn(0, 100)

    val animatedProgress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0.03f, 1f),
        animationSpec = tween(durationMillis = 350),
        label = "pipeline_progress",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pipeline_progress_card"),
        shape = MaterialTheme.shapes.large,
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioHairline),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = videoTitle?.takeIf { it.isNotBlank() } ?: "Processing YouTube Video",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (queuePosition > 0) {
                            "Queued (#$queuePosition) — $statusMessage"
                        } else {
                            statusMessage
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }

                if (!detectedLanguage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ElectricCyanContainer,
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                    ) {
                        Text(
                            text = formatLanguageBadge(detectedLanguage),
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .testTag("pipeline_progress_bar"),
                    color = KineticYellow,
                    trackColor = StudioSurfaceElevated,
                    strokeCap = StrokeCap.Round,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stepDisplayLabel(state.step),
                        style = MaterialTheme.typography.labelMedium,
                        color = KineticYellow,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$progressPercent%",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PipelineStepChip(
                    step = PipelineStep.DOWNLOADING,
                    label = stringResource(R.string.step_downloading),
                    icon = Icons.Filled.CloudDownload,
                    currentStep = state.step,
                )
                PipelineStepChip(
                    step = PipelineStep.TRANSCRIBING,
                    label = stringResource(R.string.step_transcribing),
                    icon = Icons.Filled.GraphicEq,
                    currentStep = state.step,
                )
                PipelineStepChip(
                    step = PipelineStep.FINDING_MOMENTS,
                    label = stringResource(R.string.step_finding_moments),
                    icon = Icons.Filled.AutoAwesome,
                    currentStep = state.step,
                )
                PipelineStepChip(
                    step = PipelineStep.EDITING_CLIPS,
                    label = stringResource(R.string.step_editing_clips),
                    icon = Icons.Filled.MovieCreation,
                    currentStep = state.step,
                )
                PipelineStepChip(
                    step = PipelineStep.DONE,
                    label = stringResource(R.string.step_done),
                    icon = Icons.Filled.DoneAll,
                    currentStep = state.step,
                )
            }
        }
    }
}

@Composable
private fun PipelineStepChip(
    step: PipelineStep,
    label: String,
    icon: ImageVector,
    currentStep: PipelineStep,
) {
    val isCompleted = currentStep.order > step.order
    val isActive = currentStep == step

    val bgColor = when {
        isCompleted -> EmeraldContainer
        isActive -> KineticYellowContainer
        else -> StudioSurfaceElevated
    }
    val borderColor = when {
        isCompleted -> EmeraldSuccess.copy(alpha = 0.5f)
        isActive -> KineticYellow.copy(alpha = 0.75f)
        else -> StudioHairline
    }
    val contentColor = when {
        isCompleted -> EmeraldSuccess
        isActive -> KineticYellow
        else -> TextMuted
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.testTag("step_chip_${step.name.lowercase()}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                isCompleted -> Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(EmeraldSuccess.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(12.dp),
                    )
                }
                isActive -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = KineticYellow,
                )
                else -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (isActive || isCompleted) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun stepDisplayLabel(step: PipelineStep): String = when (step) {
    PipelineStep.DOWNLOADING -> stringResource(R.string.step_downloading)
    PipelineStep.TRANSCRIBING -> stringResource(R.string.step_transcribing)
    PipelineStep.FINDING_MOMENTS -> stringResource(R.string.step_finding_moments)
    PipelineStep.EDITING_CLIPS -> stringResource(R.string.step_editing_clips)
    PipelineStep.DONE -> stringResource(R.string.step_done)
}

fun formatLanguageBadge(code: String): String = when (code.lowercase()) {
    "bn", "bengali" -> "বাংলা • Bengali"
    "hi", "hindi" -> "हिन्दी • Hindi"
    "en", "english" -> "English"
    else -> code.uppercase()
}
