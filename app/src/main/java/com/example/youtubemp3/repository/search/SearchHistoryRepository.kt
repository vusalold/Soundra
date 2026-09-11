package com.vusal.soundra.repository.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.searchDataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")

class SearchHistoryRepository(private val context: Context) {
    private val HISTORY_KEY = stringPreferencesKey("recent_searches")

    val recentSearches: Flow<List<String>> = context.searchDataStore.data.map { preferences ->
        val raw = preferences[HISTORY_KEY] ?: ""
        if (raw.isEmpty()) emptyList() else raw.split("|")
    }

    suspend fun addSearch(query: String) {
        if (query.isBlank()) return
        context.searchDataStore.edit { preferences ->
            val current = (preferences[HISTORY_KEY] ?: "").split("|").toMutableList()
            current.remove("")
            current.remove(query)
            current.add(0, query)
            val updated = current.take(30).joinToString("|")
            preferences[HISTORY_KEY] = updated
        }
    }

    suspend fun removeSearch(query: String) {
        context.searchDataStore.edit { preferences ->
            val current = (preferences[HISTORY_KEY] ?: "").split("|").toMutableList()
            current.remove(query)
            preferences[HISTORY_KEY] = current.joinToString("|")
        }
    }

    suspend fun clearHistory() {
        context.searchDataStore.edit { it.clear() }
    }
}
