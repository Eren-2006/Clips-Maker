package com.example.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.CaptionLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.clipMasterDataStore by preferencesDataStore(name = "clipmaster_settings")

class SettingsDataStore(private val context: Context) {
    companion object {
        const val DEFAULT_BACKEND_URL = "http://10.0.2.2:8000"
        private val KEY_BACKEND_URL = stringPreferencesKey("backend_base_url")
        private val KEY_TARGET_LANGUAGE = stringPreferencesKey("target_caption_language")
    }

    val backendUrlFlow: Flow<String> = context.clipMasterDataStore.data.map { prefs ->
        normalizeBaseUrl(prefs[KEY_BACKEND_URL] ?: DEFAULT_BACKEND_URL)
    }

    val targetLanguageFlow: Flow<CaptionLanguage> = context.clipMasterDataStore.data.map { prefs ->
        CaptionLanguage.fromCode(prefs[KEY_TARGET_LANGUAGE])
    }

    suspend fun saveBackendUrl(rawUrl: String) {
        val normalized = normalizeBaseUrl(rawUrl)
        context.clipMasterDataStore.edit { prefs ->
            prefs[KEY_BACKEND_URL] = normalized
        }
    }

    suspend fun saveTargetLanguage(language: CaptionLanguage) {
        context.clipMasterDataStore.edit { prefs ->
            prefs[KEY_TARGET_LANGUAGE] = language.code
        }
    }

    fun normalizeBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return DEFAULT_BACKEND_URL
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }
}
