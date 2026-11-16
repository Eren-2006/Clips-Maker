package com.example

import com.example.core.CacheCleanupManager
import com.example.core.YouTubeUrlValidator
import com.example.data.MomentSelectorAiRepository
import com.example.model.CaptionLanguage
import com.example.model.GeneratedClip
import com.example.model.SavedJobRecord
import com.example.model.TranscriptSegment
import com.example.model.toProcessingState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ClipMasterUnitTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `validates standard watch youtu_be and shorts urls`() {
        val watch = YouTubeUrlValidator.validate("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=12s")
        assertTrue(watch is YouTubeUrlValidator.ValidationResult.Valid)
        assertEquals("dQw4w9WgXcQ", (watch as YouTubeUrlValidator.ValidationResult.Valid).videoId)

        val short = YouTubeUrlValidator.validate("youtu.be/aqz-KE-bpKQ")
        assertTrue(short is YouTubeUrlValidator.ValidationResult.Valid)
        assertEquals("https://www.youtube.com/watch?v=aqz-KE-bpKQ", (short as YouTubeUrlValidator.ValidationResult.Valid).normalizedUrl)

        val shorts = YouTubeUrlValidator.validate("https://youtube.com/shorts/abcdefghijk")
        assertTrue(shorts is YouTubeUrlValidator.ValidationResult.Valid)

        val sharedText = "Watch this breakdown! https://youtu.be/dQw4w9WgXcQ?si=12345"
        val extracted = YouTubeUrlValidator.extractFirstUrl(sharedText)
        assertEquals("https://youtu.be/dQw4w9WgXcQ?si=12345", extracted)
    }

    @Test
    fun `rejects non-youtube or malformed urls`() {
        val vimeo = YouTubeUrlValidator.validate("https://vimeo.com/123456")
        assertTrue(vimeo is YouTubeUrlValidator.ValidationResult.Invalid)

        val blank = YouTubeUrlValidator.validate("   ")
        assertTrue(blank is YouTubeUrlValidator.ValidationResult.Invalid)

        val badId = YouTubeUrlValidator.validate("https://youtube.com/watch?v=short")
        assertTrue(badId is YouTubeUrlValidator.ValidationResult.Invalid)
    }

    @Test
    fun `parses structured gemini moments json cleanly`() {
        val json = """
            {
              "moments": [
                {
                  "start": 12.4,
                  "end": 48.9,
                  "title": "Why 90% of creators quit too early",
                  "description": "The brutal truth about consistency in the first 6 months.",
                  "hashtags": ["Shorts", "#CreatorTips", "YouTubeGrowth"],
                  "score": 94
                },
                {
                  "start": 100.0,
                  "end": 102.0,
                  "title": "Too short",
                  "description": "Should be filtered out",
                  "hashtags": ["#Skip"],
                  "score": 50
                }
              ]
            }
        """.trimIndent()

        val moments = MomentSelectorAiRepository.parseMomentsJson(json, maxVideoDurationSeconds = 300.0)
        assertEquals(1, moments.size)
        val first = moments.first()
        assertEquals(12.4, first.start, 0.01)
        assertEquals(48.9, first.end, 0.01)
        assertEquals(94, first.score)
        assertEquals(listOf("#Shorts", "#CreatorTips", "#YouTubeGrowth"), first.hashtags)
    }

    @Test
    fun `purges cached records and files older than 24 hours`() {
        val cacheDir = tempFolder.newFolder("cache")
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val manager = CacheCleanupManager(cacheDir, moshi)

        val now = 1_700_000_000_000L
        val twentyFiveHoursAgo = now - (25L * 60L * 60L * 1000L)
        val twoHoursAgo = now - (2L * 60L * 60L * 1000L)

        val sampleClip = GeneratedClip(
            id = "c1",
            title = "Hook",
            description = "Desc",
            hashtags = listOf("#Shorts"),
            viralityScore = 88,
            startSeconds = 10.0,
            endSeconds = 40.0,
            durationSeconds = 30.0,
            language = "en",
            previewUrl = "http://localhost:8000/clips/c1.mp4",
            downloadUrl = "http://localhost:8000/clips/c1.mp4?download=1",
        )

        manager.saveJobRecord(
            SavedJobRecord(
                jobId = "old_job",
                youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                videoTitle = "Old Video",
                detectedLanguage = "en",
                zipUrl = null,
                clips = listOf(sampleClip),
                createdAtEpochMs = twentyFiveHoursAgo,
            ),
            nowEpochMs = twentyFiveHoursAgo,
        )
        manager.saveJobRecord(
            SavedJobRecord(
                jobId = "fresh_job",
                youtubeUrl = "https://www.youtube.com/watch?v=aqz-KE-bpKQ",
                videoTitle = "Fresh Video",
                detectedLanguage = "bn",
                zipUrl = null,
                clips = listOf(sampleClip),
                createdAtEpochMs = twoHoursAgo,
            ),
            nowEpochMs = twoHoursAgo,
        )

        val expiredFile = File(manager.getShareExportsDir(), "expired.mp4").apply {
            writeText("old")
            setLastModified(twentyFiveHoursAgo)
        }
        val freshFile = File(manager.getShareExportsDir(), "fresh.mp4").apply {
            writeText("fresh")
            setLastModified(twoHoursAgo)
        }

        val remaining = manager.purgeExpired(nowEpochMs = now)
        assertEquals(1, remaining.size)
        assertEquals("fresh_job", remaining.first().jobId)
        assertFalse(expiredFile.exists())
        assertTrue(freshFile.exists())
    }

    @Test
    fun `maps pipeline steps to ProcessingState with progress fractions`() {
        val downloading = com.example.model.ClipJobUiState.Processing(
            jobId = "j1",
            currentStep = com.example.model.PipelineStep.DOWNLOADING,
            progressPercent = 20,
            statusMessage = "Downloading video...",
        ).toProcessingState()
        assertTrue(downloading is com.example.model.ProcessingState.Downloading)
        assertEquals(0.20f, downloading.progress, 0.001f)

        val transcribing = com.example.model.ClipJobUiState.Processing(
            jobId = "j1",
            currentStep = com.example.model.PipelineStep.TRANSCRIBING,
            progressPercent = 45,
            statusMessage = "Transcribing...",
            detectedLanguage = "bn",
        ).toProcessingState()
        assertTrue(transcribing is com.example.model.ProcessingState.Transcribing)
        assertEquals(0.45f, transcribing.progress, 0.001f)

        val editing = com.example.model.ClipJobUiState.Processing(
            jobId = "j1",
            currentStep = com.example.model.PipelineStep.EDITING_CLIPS,
            progressPercent = 80,
            statusMessage = "Editing clips...",
        ).toProcessingState()
        assertTrue(editing is com.example.model.ProcessingState.Editing)
        assertEquals(0.80f, editing.progress, 0.001f)

        val done = com.example.model.ClipJobUiState.Completed(
            jobId = "j1",
            videoTitle = "Test",
            detectedLanguage = "en",
            videoDurationSeconds = 120.0,
            clips = emptyList(),
            zipDownloadUrl = null,
        ).toProcessingState()
        assertTrue(done is com.example.model.ProcessingState.Done)
        assertEquals(1.0f, done.progress, 0.001f)
    }

    @Test
    fun `parses caption language codes and formats gemini target language prompt`() {
        assertEquals(CaptionLanguage.AUTO, CaptionLanguage.fromCode(null))
        assertEquals(CaptionLanguage.AUTO, CaptionLanguage.fromCode("auto"))
        assertEquals(CaptionLanguage.ENGLISH, CaptionLanguage.fromCode("en"))
        assertEquals(CaptionLanguage.HINDI, CaptionLanguage.fromCode("HI"))
        assertEquals(CaptionLanguage.BENGALI, CaptionLanguage.fromCode("bn"))
        assertEquals(CaptionLanguage.AUTO, CaptionLanguage.fromCode("unknown"))

        val repo = MomentSelectorAiRepository()
        val prompt = repo.buildPrompt(
            videoTitle = "Demo Talk",
            detectedLanguage = "en",
            videoDurationSeconds = 120.0,
            segments = listOf(TranscriptSegment(start = 0.0, end = 5.0, text = "Hello world")),
            targetLanguage = CaptionLanguage.BENGALI,
        )
        assertTrue(prompt.contains("Bengali (বাংলা) (bn)"))
        assertTrue(prompt.contains("strictly in Bengali (বাংলা)"))
    }
}
