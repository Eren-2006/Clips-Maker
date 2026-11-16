package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.CacheCleanupManager
import com.example.core.DownloadsExporter
import com.example.core.SettingsDataStore
import com.example.core.YouTubeUrlValidator
import com.example.data.ClipMasterApiClient
import com.example.data.CreateJobRequest
import com.example.data.JobStatusResponse
import com.example.data.MomentSelectorAiRepository
import com.example.data.SubmitMomentsRequest
import com.example.model.CaptionLanguage
import com.example.model.ClipDownloadState
import com.example.model.ClipErrorKind
import com.example.model.ClipJobUiState
import com.example.model.GeneratedClip
import com.example.model.PipelineStep
import com.example.model.ProcessingState
import com.example.model.SavedJobRecord
import com.example.model.toProcessingState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

private const val TAG = "ClipMasterViewModel"

sealed interface ServerTestState {
    data object Idle : ServerTestState
    data object Checking : ServerTestState
    data class Connected(val message: String) : ServerTestState
    data class Failed(val message: String) : ServerTestState
}

class ClipMasterViewModel(application: Application) : AndroidViewModel(application) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val settingsDataStore = SettingsDataStore(application)
    private val apiClient = ClipMasterApiClient(moshi)
    private val cacheCleanupManager = CacheCleanupManager(application, moshi)
    private val downloadsExporter = DownloadsExporter(
        context = application,
        okHttpClient = apiClient.okHttpClient,
        cacheCleanupManager = cacheCleanupManager,
    )
    private val momentSelectorAi = MomentSelectorAiRepository()

    val backendUrl: StateFlow<String> = settingsDataStore.backendUrlFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsDataStore.DEFAULT_BACKEND_URL,
    )

    val targetLanguage: StateFlow<CaptionLanguage> = settingsDataStore.targetLanguageFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CaptionLanguage.AUTO,
    )

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _uiState = MutableStateFlow<ClipJobUiState>(ClipJobUiState.Idle)
    val uiState: StateFlow<ClipJobUiState> = _uiState.asStateFlow()

    val processingState: StateFlow<ProcessingState> = _uiState
        .map { it.toProcessingState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ProcessingState.Idle,
        )

    private val _savedJobs = MutableStateFlow<List<SavedJobRecord>>(emptyList())
    val savedJobs: StateFlow<List<SavedJobRecord>> = _savedJobs.asStateFlow()

    private val _activePlayingClipId = MutableStateFlow<String?>(null)
    val activePlayingClipId: StateFlow<String?> = _activePlayingClipId.asStateFlow()

    private val _serverTestState = MutableStateFlow<ServerTestState>(ServerTestState.Idle)
    val serverTestState: StateFlow<ServerTestState> = _serverTestState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var activeJobRunner: Job? = null

    init {
        viewModelScope.launch {
            _savedJobs.value = cacheCleanupManager.purgeExpired()
        }
    }

    fun onUrlInputChanged(newUrl: String) {
        _urlInput.value = newUrl
        val current = _uiState.value
        if (current is ClipJobUiState.Error && current.kind == ClipErrorKind.INVALID_URL) {
            _uiState.value = ClipJobUiState.Idle
        }
    }

    fun clearUrlInput() {
        _urlInput.value = ""
        if (_uiState.value is ClipJobUiState.Error) {
            _uiState.value = ClipJobUiState.Idle
        }
    }

    fun onTargetLanguageSelected(language: CaptionLanguage) {
        viewModelScope.launch {
            settingsDataStore.saveTargetLanguage(language)
        }
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun showToastMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun setActivePlayingClip(clipId: String?) {
        _activePlayingClipId.value = clipId
    }

    fun resetToIdle() {
        activeJobRunner?.cancel()
        _activePlayingClipId.value = null
        _uiState.value = ClipJobUiState.Idle
    }

    fun saveBackendUrl(rawUrl: String) {
        viewModelScope.launch {
            settingsDataStore.saveBackendUrl(rawUrl)
            _serverTestState.value = ServerTestState.Idle
            _snackbarMessage.value = "Server URL updated"
        }
    }

    fun testServerConnection(candidateUrl: String) {
        viewModelScope.launch {
            val normalized = settingsDataStore.normalizeBaseUrl(candidateUrl)
            _serverTestState.value = ServerTestState.Checking
            try {
                val response = apiClient.serviceFor(normalized).health()
                if (response.isSuccessful) {
                    val body = response.body()
                    val queueInfo = if (body != null && body.queuedJobs > 0) {
                        " (${body.queuedJobs} job(s) queued)"
                    } else {
                        " (ready)"
                    }
                    _serverTestState.value = ServerTestState.Connected("Connected to $normalized$queueInfo")
                } else {
                    _serverTestState.value = ServerTestState.Failed(
                        "Server responded with HTTP ${response.code()}"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _serverTestState.value = ServerTestState.Failed(
                    e.localizedMessage ?: "Unable to connect to $normalized"
                )
            }
        }
    }

    fun processVideo() = generateClips()

    fun generateClips() {
        val rawUrl = _urlInput.value
        when (val validation = YouTubeUrlValidator.validate(rawUrl)) {
            is YouTubeUrlValidator.ValidationResult.Invalid -> {
                _uiState.value = ClipJobUiState.Error(
                    kind = ClipErrorKind.INVALID_URL,
                    message = validation.reason,
                )
                return
            }
            is YouTubeUrlValidator.ValidationResult.Valid -> {
                startPipelineJob(validation.normalizedUrl)
            }
        }
    }

    private fun startPipelineJob(normalizedUrl: String) {
        activeJobRunner?.cancel()
        _activePlayingClipId.value = null

        activeJobRunner = viewModelScope.launch {
            val baseUrl = settingsDataStore.backendUrlFlow.first()
            val selectedLanguage = settingsDataStore.targetLanguageFlow.first()
            val api = apiClient.serviceFor(baseUrl)

            _uiState.value = ClipJobUiState.Processing(
                jobId = "",
                currentStep = PipelineStep.DOWNLOADING,
                progressPercent = 4,
                statusMessage = "Connecting to ClipMaster pipeline...",
                detectedLanguage = selectedLanguage.takeIf { it != CaptionLanguage.AUTO }?.code,
            )

            try {
                val createResponse = api.analyzeVideo(
                    CreateJobRequest(
                        youtubeUrl = normalizedUrl,
                        mode = "client_ai",
                        targetLanguage = selectedLanguage.code,
                    )
                )

                if (!createResponse.isSuccessful) {
                    val errBody = createResponse.errorBody()?.string()
                    val (code, detail) = apiClient.parseErrorDetail(errBody)
                    _uiState.value = mapBackendError(code, detail, createResponse.code())
                    return@launch
                }

                val created = createResponse.body()
                    ?: throw IllegalStateException("Backend returned an empty job response.")

                val jobId = created.jobId
                var momentsSubmitted = false

                while (true) {
                    val statusResp = api.getJobStatus(jobId)
                    if (!statusResp.isSuccessful) {
                        val errBody = statusResp.errorBody()?.string()
                        val (code, detail) = apiClient.parseErrorDetail(errBody)
                        _uiState.value = mapBackendError(code, detail, statusResp.code())
                        return@launch
                    }

                    val status = statusResp.body()
                        ?: throw IllegalStateException("Empty job status payload.")

                    if (status.status == "failed" || status.step == "ERROR") {
                        _uiState.value = mapBackendError(
                            status.errorCode,
                            status.errorMessage ?: status.statusMessage,
                            500,
                        )
                        return@launch
                    }

                    if (status.status == "awaiting_moments" && !momentsSubmitted) {
                        _uiState.value = ClipJobUiState.Processing(
                            jobId = jobId,
                            currentStep = PipelineStep.FINDING_MOMENTS,
                            progressPercent = 52,
                            statusMessage = "Analyzing transcript with Gemini AI for best 20–60s moments...",
                            videoTitle = status.videoTitle,
                            detectedLanguage = status.detectedLanguage,
                        )

                        val selectedMoments = momentSelectorAi.selectBestMoments(
                            videoTitle = status.videoTitle.orEmpty(),
                            detectedLanguage = status.detectedLanguage ?: selectedLanguage.code.takeIf { it != "auto" } ?: "en",
                            videoDurationSeconds = status.videoDurationSeconds,
                            segments = status.transcriptSegments,
                            targetLanguage = selectedLanguage,
                        )

                        val submitResp = api.submitMoments(
                            jobId = jobId,
                            request = SubmitMomentsRequest(moments = selectedMoments),
                        )
                        if (!submitResp.isSuccessful) {
                            val errBody = submitResp.errorBody()?.string()
                            val (code, detail) = apiClient.parseErrorDetail(errBody)
                            _uiState.value = mapBackendError(code, detail, submitResp.code())
                            return@launch
                        }
                        momentsSubmitted = true
                        delay(1000L)
                        continue
                    }

                    if (status.status == "completed" || status.step == "DONE") {
                        val resolvedClips = resolveClipUrls(baseUrl, status.clips)
                        val resolvedZip = status.zipUrl?.let { apiClient.resolveUrl(baseUrl, it) }
                        val lang = status.detectedLanguage ?: "en"
                        val title = status.videoTitle ?: "YouTube Video"

                        val savedRecord = SavedJobRecord(
                            jobId = jobId,
                            youtubeUrl = normalizedUrl,
                            videoTitle = title,
                            detectedLanguage = lang,
                            zipUrl = resolvedZip,
                            clips = resolvedClips,
                            createdAtEpochMs = System.currentTimeMillis(),
                        )
                        _savedJobs.value = cacheCleanupManager.saveJobRecord(savedRecord)

                        _uiState.value = ClipJobUiState.Completed(
                            jobId = jobId,
                            videoTitle = title,
                            detectedLanguage = lang,
                            videoDurationSeconds = status.videoDurationSeconds,
                            clips = resolvedClips,
                            zipDownloadUrl = resolvedZip,
                        )
                        return@launch
                    }

                    _uiState.value = status.toProcessingUiState()
                    delay(1200L)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "Backend connection failed for $baseUrl", e)
                _uiState.value = ClipJobUiState.Error(
                    kind = ClipErrorKind.SERVER_UNREACHABLE,
                    message = getApplication<Application>().getString(
                        R.string.error_server_unreachable,
                        baseUrl,
                    ),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline execution failed", e)
                val isAiFailure = e.message?.contains("Gemini", ignoreCase = true) == true ||
                    e.message?.contains("App Check", ignoreCase = true) == true ||
                    e.message?.contains("403", ignoreCase = true) == true
                _uiState.value = ClipJobUiState.Error(
                    kind = if (isAiFailure) ClipErrorKind.AI_FAILURE else ClipErrorKind.PIPELINE_FAILURE,
                    message = e.localizedMessage ?: "An unexpected error occurred while generating clips.",
                )
            }
        }
    }

    fun openSavedJob(record: SavedJobRecord) {
        _activePlayingClipId.value = null
        _uiState.value = ClipJobUiState.Completed(
            jobId = record.jobId,
            videoTitle = record.videoTitle,
            detectedLanguage = record.detectedLanguage,
            videoDurationSeconds = record.clips.sumOf { it.durationSeconds },
            clips = record.clips,
            zipDownloadUrl = record.zipUrl,
        )
    }

    fun downloadClip(clip: GeneratedClip) {
        val current = _uiState.value as? ClipJobUiState.Completed ?: return
        if (current.downloadStates[clip.id] is ClipDownloadState.Downloading) return

        viewModelScope.launch {
            updateClipDownloadState(clip.id, ClipDownloadState.Downloading)
            val safeName = sanitizeFilename(clip.title, "clip_${clip.id}") + ".mp4"
            val result = downloadsExporter.saveToDownloads(
                fileUrl = clip.downloadUrl,
                fileName = safeName,
                mimeType = "video/mp4",
            )
            result.fold(
                onSuccess = { location ->
                    updateClipDownloadState(clip.id, ClipDownloadState.Saved(location))
                    _snackbarMessage.value = getApplication<Application>()
                        .getString(R.string.msg_saved_to_downloads)
                },
                onFailure = { err ->
                    updateClipDownloadState(clip.id, ClipDownloadState.Idle)
                    _snackbarMessage.value = err.localizedMessage ?: "Download failed."
                }
            )
        }
    }

    fun downloadAllZip() {
        val current = _uiState.value as? ClipJobUiState.Completed ?: return
        val zipUrl = current.zipDownloadUrl ?: return
        if (current.zipDownloadState is ClipDownloadState.Downloading) return

        viewModelScope.launch {
            _uiState.value = current.copy(zipDownloadState = ClipDownloadState.Downloading)
            val safeName = sanitizeFilename(current.videoTitle, "clipmaster_${current.jobId}") + "_clips.zip"
            val result = downloadsExporter.saveToDownloads(
                fileUrl = zipUrl,
                fileName = safeName,
                mimeType = "application/zip",
            )
            val latest = _uiState.value as? ClipJobUiState.Completed ?: return@launch
            result.fold(
                onSuccess = { location ->
                    _uiState.value = latest.copy(zipDownloadState = ClipDownloadState.Saved(location))
                    _snackbarMessage.value = getApplication<Application>()
                        .getString(R.string.msg_zip_saved_to_downloads)
                },
                onFailure = { err ->
                    _uiState.value = latest.copy(zipDownloadState = ClipDownloadState.Idle)
                    _snackbarMessage.value = err.localizedMessage ?: "ZIP download failed."
                }
            )
        }
    }

    fun prepareShareClip(clip: GeneratedClip, onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            val safeName = sanitizeFilename(clip.title, "clip_${clip.id}") + ".mp4"
            val result = downloadsExporter.downloadToShareUri(
                fileUrl = clip.downloadUrl,
                fileName = safeName,
            )
            result.fold(
                onSuccess = onReady,
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Unable to prepare clip for sharing."
                }
            )
        }
    }

    private fun updateClipDownloadState(clipId: String, state: ClipDownloadState) {
        val latest = _uiState.value as? ClipJobUiState.Completed ?: return
        _uiState.value = latest.copy(
            downloadStates = latest.downloadStates + (clipId to state)
        )
    }

    private fun resolveClipUrls(baseUrl: String, clips: List<GeneratedClip>): List<GeneratedClip> {
        return clips.map { clip ->
            clip.copy(
                previewUrl = apiClient.resolveUrl(baseUrl, clip.previewUrl),
                downloadUrl = apiClient.resolveUrl(baseUrl, clip.downloadUrl),
            )
        }
    }

    private fun JobStatusResponse.toProcessingUiState(): ClipJobUiState.Processing {
        val mappedStep = when (step.uppercase()) {
            "DOWNLOADING" -> PipelineStep.DOWNLOADING
            "TRANSCRIBING" -> PipelineStep.TRANSCRIBING
            "FINDING_MOMENTS" -> PipelineStep.FINDING_MOMENTS
            "EDITING_CLIPS" -> PipelineStep.EDITING_CLIPS
            "DONE" -> PipelineStep.DONE
            else -> PipelineStep.DOWNLOADING
        }
        return ClipJobUiState.Processing(
            jobId = jobId,
            currentStep = mappedStep,
            progressPercent = progressPercent.coerceIn(0, 100),
            statusMessage = statusMessage.ifBlank { "Processing video..." },
            videoTitle = videoTitle,
            detectedLanguage = detectedLanguage,
            queuePosition = queuePosition,
        )
    }

    private fun mapBackendError(
        errorCode: String?,
        detail: String?,
        httpCode: Int,
    ): ClipJobUiState.Error {
        val kind = when (errorCode?.uppercase()) {
            "INVALID_URL" -> ClipErrorKind.INVALID_URL
            "PRIVATE_VIDEO", "VIDEO_UNAVAILABLE" -> ClipErrorKind.PRIVATE_VIDEO
            "VIDEO_TOO_LONG" -> ClipErrorKind.VIDEO_TOO_LONG
            "API_FAILURE" -> ClipErrorKind.AI_FAILURE
            else -> ClipErrorKind.PIPELINE_FAILURE
        }
        val friendlyMessage = detail?.takeIf { it.isNotBlank() } ?: when (kind) {
            ClipErrorKind.INVALID_URL -> "That YouTube link could not be recognized."
            ClipErrorKind.PRIVATE_VIDEO -> "This video is private, deleted, or restricted."
            ClipErrorKind.VIDEO_TOO_LONG -> "Videos longer than 2 hours cannot be processed."
            ClipErrorKind.AI_FAILURE -> "AI moment analysis failed. Please try again."
            else -> "Backend processing error (HTTP $httpCode)."
        }
        return ClipJobUiState.Error(kind = kind, message = friendlyMessage)
    }

    private fun sanitizeFilename(raw: String, fallback: String): String {
        val cleaned = raw.lowercase()
            .replace(Regex("[^a-z0-9\\u0980-\\u09FF\\u0900-\\u097F]+"), "_")
            .trim('_')
            .take(40)
        return cleaned.ifBlank { fallback }
    }
}
