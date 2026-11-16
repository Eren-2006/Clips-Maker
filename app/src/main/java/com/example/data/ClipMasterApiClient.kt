package com.example.data

import com.example.model.GeneratedClip
import com.example.model.SelectedMoment
import com.example.model.TranscriptSegment
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String,
    val service: String? = null,
    @Json(name = "active_job_id") val activeJobId: String? = null,
    @Json(name = "queued_jobs") val queuedJobs: Int = 0,
)

@JsonClass(generateAdapter = true)
data class CreateJobRequest(
    @Json(name = "youtube_url") val youtubeUrl: String,
    val mode: String = "client_ai",
    @Json(name = "target_language") val targetLanguage: String = "auto",
)

@JsonClass(generateAdapter = true)
data class CreateJobResponse(
    @Json(name = "job_id") val jobId: String,
    @Json(name = "queue_position") val queuePosition: Int = 0,
)

@JsonClass(generateAdapter = true)
data class SubmitMomentsRequest(
    val moments: List<SelectedMoment>,
)

@JsonClass(generateAdapter = true)
data class JobStatusResponse(
    @Json(name = "job_id") val jobId: String,
    val status: String,
    val step: String,
    @Json(name = "progress_percent") val progressPercent: Int = 0,
    @Json(name = "status_message") val statusMessage: String = "",
    @Json(name = "queue_position") val queuePosition: Int = 0,
    @Json(name = "video_title") val videoTitle: String? = null,
    @Json(name = "video_duration_seconds") val videoDurationSeconds: Double = 0.0,
    @Json(name = "detected_language") val detectedLanguage: String? = null,
    @Json(name = "transcript_segments") val transcriptSegments: List<TranscriptSegment> = emptyList(),
    val clips: List<GeneratedClip> = emptyList(),
    @Json(name = "zip_url") val zipUrl: String? = null,
    @Json(name = "error_code") val errorCode: String? = null,
    @Json(name = "error_message") val errorMessage: String? = null,
)

@JsonClass(generateAdapter = true)
data class ApiErrorPayload(
    val detail: String? = null,
    @Json(name = "error_code") val errorCode: String? = null,
    val message: String? = null,
)

interface ClipMasterApiService {
    @GET("health")
    suspend fun health(): Response<HealthResponse>

    @POST("api/analyze")
    suspend fun analyzeVideo(@Body request: CreateJobRequest): Response<CreateJobResponse>

    @POST("api/jobs")
    suspend fun createJob(@Body request: CreateJobRequest): Response<CreateJobResponse>

    @GET("api/jobs/{jobId}")
    suspend fun getJobStatus(@Path("jobId") jobId: String): Response<JobStatusResponse>

    @POST("api/jobs/{jobId}/moments")
    suspend fun submitMoments(
        @Path("jobId") jobId: String,
        @Body request: SubmitMomentsRequest,
    ): Response<JobStatusResponse>
}

class ClipMasterApiClient(
    val moshi: Moshi,
    val okHttpClient: OkHttpClient = buildDefaultOkHttpClient(),
) {
    private var cachedBaseUrl: String? = null
    private var cachedService: ClipMasterApiService? = null

    @Synchronized
    fun serviceFor(baseUrl: String): ClipMasterApiService {
        val clean = baseUrl.trim().trimEnd('/') + "/"
        if (clean == cachedBaseUrl && cachedService != null) {
            return cachedService!!
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(clean)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(ClipMasterApiService::class.java).also {
            cachedBaseUrl = clean
            cachedService = it
        }
    }

    fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
        if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
            return relativeOrAbsolute
        }
        val cleanBase = baseUrl.trim().trimEnd('/')
        val cleanPath = relativeOrAbsolute.trimStart('/')
        return "$cleanBase/$cleanPath"
    }

    fun parseErrorDetail(errorBody: String?): Pair<String?, String?> {
        if (errorBody.isNullOrBlank()) return null to null
        return try {
            val adapter = moshi.adapter(ApiErrorPayload::class.java)
            val parsed = adapter.fromJson(errorBody)
            parsed?.errorCode to (parsed?.message ?: parsed?.detail)
        } catch (_: Exception) {
            null to errorBody
        }
    }

    companion object {
        fun buildDefaultOkHttpClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()
        }
    }
}
