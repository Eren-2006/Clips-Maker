package com.example.data

import android.util.Log
import com.example.model.CaptionLanguage
import com.example.model.SelectedMoment
import com.example.model.TranscriptSegment
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

private const val TAG = "MomentSelectorAiRepo"

class MomentSelectorAiRepository {
    private val momentsSchema = Schema.obj(
        properties = mapOf(
            "moments" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "start" to Schema.double(description = "Clip start timestamp in seconds, aligned to the start of a complete sentence."),
                        "end" to Schema.double(description = "Clip end timestamp in seconds, aligned to the end of a complete sentence (20 to 60 seconds after start)."),
                        "title" to Schema.string(description = "Catchy, high-CTR vertical short title (max 60 characters) matching the requested caption language."),
                        "description" to Schema.string(description = "Short 1-2 sentence social caption summarizing the hook or takeaway in the requested caption language."),
                        "hashtags" to Schema.array(
                            items = Schema.string(),
                            description = "3 to 5 relevant hashtags including #Shorts."
                        ),
                        "score" to Schema.integer(description = "Virality score from 1 to 100 based on hook strength, emotion, insight, humor, or surprise.")
                    )
                ),
                description = "5 to 10 best viral short moments from the transcript."
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = momentsSchema
                temperature = 0.4f
            }
        )
    }

    suspend fun selectBestMoments(
        videoTitle: String,
        detectedLanguage: String,
        videoDurationSeconds: Double,
        segments: List<TranscriptSegment>,
        targetLanguage: CaptionLanguage = CaptionLanguage.AUTO,
    ): List<SelectedMoment> {
        if (segments.isEmpty()) {
            throw IllegalStateException("Transcript is empty; no speech segments were detected.")
        }

        val prompt = buildPrompt(
            videoTitle = videoTitle,
            detectedLanguage = detectedLanguage,
            videoDurationSeconds = videoDurationSeconds,
            segments = segments,
            targetLanguage = targetLanguage,
        )

        try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val jsonText = response.text
                ?: throw IllegalStateException("Gemini returned an empty response.")

            val parsed = parseMomentsJson(jsonText, videoDurationSeconds)
            if (parsed.isEmpty()) {
                throw IllegalStateException("Gemini did not return any valid 20-60s clip moments.")
            }
            return parsed
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI moment selection timed out", e)
            throw IllegalStateException("Gemini moment analysis timed out after 60 seconds. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI moment selection failed", e)
            throw IllegalStateException(
                e.localizedMessage ?: "Gemini AI failed to analyze transcript moments.",
                e
            )
        }
    }

    internal fun buildPrompt(
        videoTitle: String,
        detectedLanguage: String,
        videoDurationSeconds: Double,
        segments: List<TranscriptSegment>,
        targetLanguage: CaptionLanguage = CaptionLanguage.AUTO,
    ): String {
        val transcriptLines = segments.joinToString("\n") { seg ->
            "[${"%.2f".format(seg.start)} -> ${"%.2f".format(seg.end)}] ${seg.text.trim()}"
        }

        val effectiveCode = if (targetLanguage != CaptionLanguage.AUTO) {
            targetLanguage.code
        } else {
            detectedLanguage.lowercase()
        }

        val languageLabel = when (effectiveCode) {
            "bn", "bengali" -> "Bengali (বাংলা)"
            "hi", "hindi" -> "Hindi (हिन्दी)"
            else -> "English"
        }

        val languageRule = if (targetLanguage != CaptionLanguage.AUTO) {
            "4. Write EVERY clip's title, short description, and hashtags strictly in $languageLabel."
        } else {
            "4. Write the title, short description, and hashtags primarily in $languageLabel (or natural bilingual style matching the speaker)."
        }

        return """
            You are an expert short-form viral video editor for YouTube Shorts, Instagram Reels, and TikTok.
            Analyze the timestamped transcript below and select the 5 to 10 BEST moments (or fewer only if the source video is under 2 minutes).

            Video Title: $videoTitle
            Target Caption Language: $languageLabel ($effectiveCode)
            Total Video Duration: ${"%.1f".format(videoDurationSeconds)} seconds

            STRICT SELECTION RULES:
            1. Each clip MUST be between 20 and 60 seconds long (unless the entire source video is shorter than 25 seconds, in which case use the best complete segment).
            2. Start and end timestamps MUST align cleanly with the start and end of COMPLETE sentences from the transcript timestamps below—never cut mid-sentence.
            3. Prioritize moments with a strong opening hook, emotional spike, sharp insight, funny punchline, or surprising reveal.
            $languageRule
            5. Assign an honest virality score between 1 and 100.
            6. Avoid overlapping clips.

            TIMESTAMPED TRANSCRIPT:
            $transcriptLines
        """.trimIndent()
    }

    companion object {
        fun parseMomentsJson(
            jsonString: String,
            maxVideoDurationSeconds: Double,
        ): List<SelectedMoment> {
            val root = JSONObject(jsonString)
            val array = root.getJSONArray("moments")
            val results = mutableListOf<SelectedMoment>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawStart = max(0.0, obj.optDouble("start", 0.0))
                val rawEnd = min(
                    if (maxVideoDurationSeconds > 0) maxVideoDurationSeconds else Double.MAX_VALUE,
                    obj.optDouble("end", rawStart + 30.0)
                )

                val duration = rawEnd - rawStart
                if (duration < 5.0) continue

                val clampedEnd = if (duration > 65.0) rawStart + 60.0 else rawEnd
                val title = obj.optString("title", "").trim()
                if (title.isBlank()) continue

                val description = obj.optString("description", "").trim()
                val score = obj.optInt("score", 75).coerceIn(1, 100)

                val tagsArray = obj.optJSONArray("hashtags")
                val hashtags = buildList {
                    if (tagsArray != null) {
                        for (j in 0 until tagsArray.length()) {
                            val tag = tagsArray.optString(j, "").trim()
                            if (tag.isNotBlank()) {
                                add(if (tag.startsWith("#")) tag else "#$tag")
                            }
                        }
                    }
                    if (isEmpty()) add("#Shorts")
                }.distinct().take(6)

                results.add(
                    SelectedMoment(
                        start = rawStart,
                        end = clampedEnd,
                        title = title,
                        description = description,
                        hashtags = hashtags,
                        score = score,
                    )
                )
            }

            return results.sortedByDescending { it.score }.take(10)
        }
    }
}
