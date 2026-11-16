package com.example.core

import android.content.Context
import android.util.Log
import com.example.model.SavedJobRecord
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File

private const val TAG = "CacheCleanupManager"
private const val HISTORY_FILE_NAME = "clipmaster_saved_jobs.json"
private const val EXPORTS_CACHE_DIR = "clipmaster_exports"

class CacheCleanupManager(
    private val cacheDir: File,
    private val moshi: Moshi,
) {
    constructor(context: Context, moshi: Moshi) : this(context.cacheDir, moshi)

    companion object {
        const val RETENTION_MILLIS: Long = 24L * 60L * 60L * 1000L
    }

    private val historyFile = File(cacheDir, HISTORY_FILE_NAME)
    private val exportsDir = File(cacheDir, EXPORTS_CACHE_DIR)
    private val listType = Types.newParameterizedType(List::class.java, SavedJobRecord::class.java)
    private val adapter = moshi.adapter<List<SavedJobRecord>>(listType)

    @Synchronized
    fun purgeExpired(nowEpochMs: Long = System.currentTimeMillis()): List<SavedJobRecord> {
        val cutoff = nowEpochMs - RETENTION_MILLIS

        if (exportsDir.exists() && exportsDir.isDirectory) {
            exportsDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) {
                    val deleted = file.deleteRecursively()
                    Log.d(TAG, "Auto-deleted expired cached export ${file.name}: $deleted")
                }
            }
        }

        val current = loadRecordsRaw()
        val active = current.filter { it.createdAtEpochMs >= cutoff }
        if (active.size != current.size) {
            saveRecordsRaw(active)
            Log.d(TAG, "Pruned ${current.size - active.size} expired 24h job records.")
        }
        return active
    }

    @Synchronized
    fun saveJobRecord(record: SavedJobRecord, nowEpochMs: Long = System.currentTimeMillis()): List<SavedJobRecord> {
        val active = purgeExpired(nowEpochMs)
            .filterNot { it.jobId == record.jobId }
            .toMutableList()
        active.add(0, record)
        saveRecordsRaw(active)
        return active
    }

    @Synchronized
    fun loadActiveRecords(nowEpochMs: Long = System.currentTimeMillis()): List<SavedJobRecord> {
        return purgeExpired(nowEpochMs)
    }

    fun getShareExportsDir(): File {
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        }
        return exportsDir
    }

    private fun loadRecordsRaw(): List<SavedJobRecord> {
        if (!historyFile.exists()) return emptyList()
        return try {
            val content = historyFile.readText()
            if (content.isBlank()) emptyList() else adapter.fromJson(content).orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cached job history", e)
            emptyList()
        }
    }

    private fun saveRecordsRaw(records: List<SavedJobRecord>) {
        try {
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            historyFile.writeText(adapter.toJson(records))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist cached job history", e)
        }
    }
}
