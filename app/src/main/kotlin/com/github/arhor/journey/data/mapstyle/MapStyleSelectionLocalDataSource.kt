package com.github.arhor.journey.data.mapstyle

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

@Singleton
class MapStyleSelectionLocalDataSource @Inject constructor(
    private val preferencesDataStore: DataStore<Preferences>,
) {
    fun observeSelectedStyleId(defaultStyleId: String): Flow<String> =
        preferencesDataStore.data.map { prefs ->
            prefs[selectedMapStyleId] ?: defaultStyleId
        }

    suspend fun setSelectedStyleId(styleId: String) {
        preferencesDataStore.edit { prefs ->
            prefs[selectedMapStyleId] = styleId
        }
    }

    companion object {
        private val selectedMapStyleId = stringPreferencesKey("selected_map_style_id")
    }
}
