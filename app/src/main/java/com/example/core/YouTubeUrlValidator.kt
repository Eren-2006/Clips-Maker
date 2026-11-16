package com.example.core

import java.net.URI

object YouTubeUrlValidator {
    private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")
    private val ALLOWED_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be",
        "www.youtu.be",
    )

    sealed interface ValidationResult {
        data class Valid(val normalizedUrl: String, val videoId: String) : ValidationResult
        data class Invalid(val reason: String) : ValidationResult
    }

    private val URL_EXTRACT_REGEX = Regex("""https?://\S+|(?:www\.)?(?:youtube\.com|youtu\.be)/\S+""")

    fun extractFirstUrl(rawText: String): String {
        val trimmed = rawText.trim()
        return URL_EXTRACT_REGEX.find(trimmed)?.value?.trimEnd(',', '.', ')', ']', '"', '\'') ?: trimmed
    }

    fun validate(rawInput: String): ValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("Please paste a YouTube video URL.")
        }

        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val uri = try {
            URI(withScheme)
        } catch (_: Exception) {
            return ValidationResult.Invalid("This doesn't look like a valid URL.")
        }

        val host = uri.host?.lowercase()
            ?: return ValidationResult.Invalid("Please enter a complete YouTube link.")

        if (host !in ALLOWED_HOSTS) {
            return ValidationResult.Invalid("Only YouTube URLs (youtube.com or youtu.be) are supported.")
        }

        val videoId = extractVideoId(uri, host)
            ?: return ValidationResult.Invalid("Could not find a valid YouTube video ID in that link.")

        if (!VIDEO_ID_REGEX.matches(videoId)) {
            return ValidationResult.Invalid("The YouTube video ID appears incomplete or malformed.")
        }

        return ValidationResult.Valid(
            normalizedUrl = "https://www.youtube.com/watch?v=$videoId",
            videoId = videoId,
        )
    }

    private fun extractVideoId(uri: URI, host: String): String? {
        val path = uri.path.orEmpty().trim('/')
        if (host.endsWith("youtu.be")) {
            return path.split('/').firstOrNull()?.takeIf { it.isNotBlank() }
        }

        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null

        return when (segments[0]) {
            "watch" -> parseQueryParam(uri.rawQuery, "v")
            "shorts", "live", "embed", "v" -> segments.getOrNull(1)
            else -> parseQueryParam(uri.rawQuery, "v")
        }
    }

    private fun parseQueryParam(rawQuery: String?, key: String): String? {
        if (rawQuery.isNullOrBlank()) return null
        return rawQuery.split('&')
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null else pair.substring(0, idx) to pair.substring(idx + 1)
            }
            .firstOrNull { it.first == key }
            ?.second
            ?.takeIf { it.isNotBlank() }
    }
}
