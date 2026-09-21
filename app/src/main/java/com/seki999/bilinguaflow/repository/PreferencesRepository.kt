package com.seki999.bilinguaflow.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.seki999.bilinguaflow.model.LanguageOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bilinguaflow_settings")

/**
 * Persists lightweight user preferences: the selected recognition language, and (optionally) the
 * most recent transcript so it survives an app restart, not just a configuration change.
 */
class PreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    private object Keys {
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val TRANSCRIPT = stringPreferencesKey("last_transcript")
    }

    val languageTag: Flow<String> = dataStore.data.map { it[Keys.LANGUAGE_TAG] ?: LanguageOptions.DEFAULT.tag }

    val savedTranscript: Flow<String> = dataStore.data.map { it[Keys.TRANSCRIPT] ?: "" }

    suspend fun saveLanguageTag(tag: String) {
        dataStore.edit { it[Keys.LANGUAGE_TAG] = tag }
    }

    suspend fun saveTranscript(text: String) {
        dataStore.edit { it[Keys.TRANSCRIPT] = text }
    }
}
