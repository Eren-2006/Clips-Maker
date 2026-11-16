package com.example.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DownloadsExporter(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val cacheCleanupManager: CacheCleanupManager,
) {
    suspend fun saveToDownloads(
        fileUrl: String,
        fileName: String,
        mimeType: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(fileUrl).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Download failed (HTTP ${response.code}).")
                    )
                }
                val body = response.body
                    ?: return@withContext Result.failure(IOException("Empty download stream."))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(
                            MediaStore.Downloads.RELATIVE_PATH,
                            "${Environment.DIRECTORY_DOWNLOADS}/ClipMaster"
                        )
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext Result.failure(
                            IOException("Could not create Downloads entry.")
                        )

                    try {
                        resolver.openOutputStream(uri)?.use { out ->
                            body.byteStream().copyTo(out)
                        } ?: throw IOException("Failed to open Downloads output stream.")

                        val completed = ContentValues().apply {
                            put(MediaStore.Downloads.IS_PENDING, 0)
                        }
                        resolver.update(uri, completed, null, null)
                        Result.success("Downloads/ClipMaster/$fileName")
                    } catch (e: Exception) {
                        resolver.delete(uri, null, null)
                        throw e
                    }
                } else {
                    val dir = File(
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                        "ClipMaster"
                    )
                    if (!dir.exists()) dir.mkdirs()
                    val target = File(dir, fileName)
                    FileOutputStream(target).use { out ->
                        body.byteStream().copyTo(out)
                    }
                    Result.success(target.absolutePath)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadToShareUri(
        fileUrl: String,
        fileName: String,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val dir = cacheCleanupManager.getShareExportsDir()
            val target = File(dir, fileName)
            if (!target.exists() || target.length() == 0L) {
                val request = Request.Builder().url(fileUrl).get().build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IOException("Could not fetch clip for sharing (HTTP ${response.code}).")
                        )
                    }
                    val body = response.body
                        ?: return@withContext Result.failure(IOException("Empty response body."))
                    FileOutputStream(target).use { out ->
                        body.byteStream().copyTo(out)
                    }
                }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target
            )
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
