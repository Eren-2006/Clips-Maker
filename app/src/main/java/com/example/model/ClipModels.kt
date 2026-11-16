package com.example.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class CaptionLanguage(
    val code: String,
    val displayLabel: String,
    val scriptHint: String,
) {
    AUTO("auto", "Auto-detect", "English, हिन्दी, বাংলা"),
    ENGLISH("en", "English", "EN • Noto Sans"),
    HINDI("hi", "Hindi (हिन्दी)", "HI • Devanagari"),
    BENGALI("bn", "Bengali (বাংলা)", "BN • Noto Sans Bengali");

    companion object {
        fun fromCode(code: String?): CaptionLanguage {
            val normalized = code?.trim()?.lowercase()
            return entries.firstOrNull { it.code == normalized } ?: AUTO
        }
    }
}

enum class PipelineStep(val order: Int) {
    DOWNLOADING(0),
    TRANSCRIBING(1),
    FINDING_MOMENTS(2),
    EDITING_CLIPS(3),
    DONE(4),
}

enum class ClipErrorKind {
    INVALID_URL,
    PRIVATE_VIDEO,
    VIDEO_TOO_LONG,
    SERVER_UNREACHABLE,
    AI_FAILURE,
    PIPELINE_FAILURE,
}

sealed interface ClipDownloadState {
    data object Idle : ClipDownloadState
    data object Downloading : ClipDownloadState
    data class Saved(val displayLocation: String) : ClipDownloadState
}

@JsonClass(generateAdapter = true)
data class TranscriptSegment(
    val start: Double,
    val end: Double,
    val text: String,
)

@JsonClass(generateAdapter = true)
data class SelectedMoment(
    val start: Double,
    val end: Double,
    val title: String,
    val description: String,
    val hashtags: List<String>,
    val score: Int,
)

@JsonClass(generateAdapter = true)
data class GeneratedClip(
    val id: String,
    val title: String,
    val description: String,
    val hashtags: List<String>,
    @Json(name = "score") val viralityScore: Int,
    @Json(name = "start") val startSeconds: Double,
    @Json(name = "end") val endSeconds: Double,
    @Json(name = "duration") val durationSeconds: Double,
    val language: String,
    @Json(name = "preview_url") val previewUrl: String,
    @Json(name = "download_url") val downloadUrl: String,
) {
    fun formattedCopyText(): String {
        val tagsLine = hashtags.joinToString(" ") { tag ->
            if (tag.startsWith("#")) tag else "#$tag"
        }
        return buildString {
            appendLine(title.trim())
            if (description.isNotBlank()) {
                appendLine()
                appendLine(description.trim())
            }
            if (tagsLine.isNotBlank()) {
                appendLine()
                append(tagsLine)
            }
        }
    }
}

@JsonClass(generateAdapter = true)
data class SavedJobRecord(
    @Json(name = "job_id") val jobId: String,
    @Json(name = "youtube_url") val youtubeUrl: String,
    @Json(name = "video_title") val videoTitle: String,
    @Json(name = "detected_language") val detectedLanguage: String,
    @Json(name = "zip_url") val zipUrl: String?,
    val clips: List<GeneratedClip>,
    @Json(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
)

sealed interface ClipJobUiState {
    data object Idle : ClipJobUiState

    data class Processing(
        val jobId: String,
        val currentStep: PipelineStep,
        val progressPercent: Int,
        val statusMessage: String,
        val videoTitle: String? = null,
        val detectedLanguage: String? = null,
        val queuePosition: Int = 0,
    ) : ClipJobUiState

    data class Completed(
        val jobId: String,
        val videoTitle: String,
        val detectedLanguage: String,
        val videoDurationSeconds: Double,
        val clips: List<GeneratedClip>,
        val zipDownloadUrl: String?,
        val downloadStates: Map<String, ClipDownloadState> = emptyMap(),
        val zipDownloadState: ClipDownloadState = ClipDownloadState.Idle,
    ) : ClipJobUiState

    data class Error(
        val kind: ClipErrorKind,
        val message: String,
        val canRetry: Boolean = true,
    ) : ClipJobUiState
}

sealed interface ProcessingState {
    val progress: Float
    val step: PipelineStep

    data object Idle : ProcessingState {
        override val progress: Float = 0f
        override val step: PipelineStep = PipelineStep.DOWNLOADING
    }

    data class Downloading(
        override val progress: Float,
        val message: String,
        val videoTitle: String? = null,
        val queuePosition: Int = 0,
    ) : ProcessingState {
        override val step: PipelineStep = PipelineStep.DOWNLOADING
    }

    data class Transcribing(
        override val progress: Float,
        val message: String,
        val videoTitle: String? = null,
        val detectedLanguage: String? = null,
    ) : ProcessingState {
        override val step: PipelineStep = PipelineStep.TRANSCRIBING
    }

    data class FindingMoments(
        override val progress: Float,
        val message: String,
        val videoTitle: String? = null,
        val detectedLanguage: String? = null,
    ) : ProcessingState {
        override val step: PipelineStep = PipelineStep.FINDING_MOMENTS
    }

    data class Editing(
        override val progress: Float,
        val message: String,
        val videoTitle: String? = null,
        val detectedLanguage: String? = null,
    ) : ProcessingState {
        override val step: PipelineStep = PipelineStep.EDITING_CLIPS
    }

    data class Done(
        override val progress: Float = 1f,
        val clipCount: Int,
        val message: String,
        val videoTitle: String? = null,
        val detectedLanguage: String? = null,
    ) : ProcessingState {
        override val step: PipelineStep = PipelineStep.DONE
    }

    data class Error(
        val message: String,
    ) : ProcessingState {
        override val progress: Float = 0f
        override val step: PipelineStep = PipelineStep.DOWNLOADING
    }
}

fun ClipJobUiState.toProcessingState(): ProcessingState = when (this) {
    ClipJobUiState.Idle -> ProcessingState.Idle
    is ClipJobUiState.Processing -> {
        val fraction = (progressPercent / 100f).coerceIn(0f, 1f)
        when (currentStep) {
            PipelineStep.DOWNLOADING -> ProcessingState.Downloading(
                progress = fraction,
                message = statusMessage,
                videoTitle = videoTitle,
                queuePosition = queuePosition,
            )
            PipelineStep.TRANSCRIBING -> ProcessingState.Transcribing(
                progress = fraction,
                message = statusMessage,
                videoTitle = videoTitle,
                detectedLanguage = detectedLanguage,
            )
            PipelineStep.FINDING_MOMENTS -> ProcessingState.FindingMoments(
                progress = fraction,
                message = statusMessage,
                videoTitle = videoTitle,
                detectedLanguage = detectedLanguage,
            )
            PipelineStep.EDITING_CLIPS -> ProcessingState.Editing(
                progress = fraction,
                message = statusMessage,
                videoTitle = videoTitle,
                detectedLanguage = detectedLanguage,
            )
            PipelineStep.DONE -> ProcessingState.Done(
                progress = 1f,
                clipCount = 0,
                message = statusMessage,
                videoTitle = videoTitle,
                detectedLanguage = detectedLanguage,
            )
        }
    }
    is ClipJobUiState.Completed -> ProcessingState.Done(
        progress = 1f,
        clipCount = clips.size,
        message = "${clips.size} ready-to-post clips created",
        videoTitle = videoTitle,
        detectedLanguage = detectedLanguage,
    )
    is ClipJobUiState.Error -> ProcessingState.Error(message = message)
}

