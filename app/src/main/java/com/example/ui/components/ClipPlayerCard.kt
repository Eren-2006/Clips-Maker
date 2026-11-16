package com.example.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.model.ClipDownloadState
import com.example.model.GeneratedClip
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.KineticYellow
import com.example.ui.theme.KineticYellowContainer
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.fontFamilyForText
import kotlin.math.roundToInt

@kotlin.OptIn(ExperimentalLayoutApi::class)
@OptIn(UnstableApi::class)
@Composable
fun ClipPlayerCard(
    clip: GeneratedClip,
    isActivePlayer: Boolean,
    downloadState: ClipDownloadState,
    onTogglePlay: () -> Unit,
    onDownloadClick: () -> Unit,
    onCopyCaptionClick: () -> Unit,
    onShareClick: () -> Unit,
    onExpandFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    val exoPlayer = remember(clip.previewUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(clip.previewUrl))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
        }
    }

    LaunchedEffect(isActivePlayer) {
        exoPlayer.playWhenReady = isActivePlayer
        isPlaying = isActivePlayer
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val scoreColor = when {
        clip.viralityScore >= 85 -> EmeraldSuccess
        clip.viralityScore >= 70 -> KineticYellow
        else -> ElectricCyan
    }
    val scoreBg = when {
        clip.viralityScore >= 85 -> EmeraldContainer
        clip.viralityScore >= 70 -> KineticYellowContainer
        else -> ElectricCyanContainer
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("clip_card_${clip.id}"),
        shape = MaterialTheme.shapes.large,
        color = StudioSurface,
        border = BorderStroke(1.dp, if (isActivePlayer) KineticYellow.copy(alpha = 0.65f) else StudioHairline),
        tonalElevation = 2.dp,
    ) {
        Column {
            // 9:16 Vertical Preview Player Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(Color.Black)
                    .clickable(onClick = onTogglePlay)
                    .testTag("clip_video_player_${clip.id}"),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    update = { view ->
                        if (view.player !== exoPlayer) {
                            view.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Top & bottom subtle readability scrims
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                            )
                        )
                )

                // Top bar overlays: Virality Score + Duration
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = scoreBg.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.55f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Whatshot,
                                contentDescription = "Virality score",
                                tint = scoreColor,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = stringResource(R.string.label_virality_score, clip.viralityScore),
                                style = MaterialTheme.typography.labelSmall,
                                color = scoreColor,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Black.copy(alpha = 0.68f),
                    ) {
                        Text(
                            text = formatDurationLabel(clip.durationSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }

                // Center Play / Pause Indicator
                if (!isPlaying) {
                    Surface(
                        modifier = Modifier
                            .size(60.dp)
                            .align(Alignment.Center),
                        shape = CircleShape,
                        color = KineticYellow.copy(alpha = 0.94f),
                        shadowElevation = 8.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play clip preview",
                                tint = Color(0xFF0A0B0E),
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                }

                // Bottom Overlay Controls: Play/Pause, Mute, Fullscreen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clip_play_toggle_${clip.id}"),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.55f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause clip" else "Play clip",
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { isMuted = !isMuted },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clip_mute_toggle_${clip.id}"),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.55f),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "Unmute clip" else "Mute clip",
                            )
                        }

                        IconButton(
                            onClick = onExpandFullscreen,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clip_fullscreen_${clip.id}"),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.55f),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fullscreen,
                                contentDescription = "Fullscreen preview",
                            )
                        }
                    }
                }
            }

            // Metadata & Actions Body
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = clip.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = fontFamilyForText(clip.title)
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (clip.description.isNotBlank()) {
                    Text(
                        text = clip.description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = fontFamilyForText(clip.description)
                        ),
                        color = TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (clip.hashtags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        clip.hashtags.take(5).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StudioSurfaceElevated,
                            ) {
                                Text(
                                    text = if (tag.startsWith("#")) tag else "#$tag",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isDownloading = downloadState is ClipDownloadState.Downloading
                    val isSaved = downloadState is ClipDownloadState.Saved

                    Button(
                        onClick = onDownloadClick,
                        enabled = !isDownloading,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("clip_download_button_${clip.id}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSaved) EmeraldContainer else KineticYellow,
                            contentColor = if (isSaved) EmeraldSuccess else Color(0xFF0A0B0E),
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF0A0B0E),
                            )
                        } else {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.Check else Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSaved) {
                                stringResource(R.string.action_downloaded)
                            } else {
                                stringResource(R.string.action_download)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    OutlinedButton(
                        onClick = onCopyCaptionClick,
                        modifier = Modifier
                            .weight(1.15f)
                            .minimumInteractiveComponentSize()
                            .testTag("clip_copy_button_${clip.id}"),
                        border = BorderStroke(1.dp, StudioHairline),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_copy_caption),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    IconButton(
                        onClick = onShareClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clip_share_button_${clip.id}"),
                        colors = IconButtonDefaults.outlinedIconButtonColors(
                            containerColor = StudioSurfaceElevated,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.action_share),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatDurationLabel(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val mins = total / 60
    val secs = total % 60
    return "%d:%02d".format(mins, secs)
}
