package com.github.arhor.journey.data.mapstyle

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Singleton
class RemoteMapStyleLocalDataSource @Inject constructor(
    private val preferencesDataStore: DataStore<Preferences>,
    private val json: Json,
) {
    fun observeCachedStyles(): Flow<List<MapStyleRecord>> =
        preferencesDataStore.data.map { prefs ->
            val rawJson = prefs[remoteStylesCache].orEmpty()
            if (rawJson.isBlank()) {
                emptyList()
            } else {
                runCatching {
                    json.decodeFromString<List<RemoteStyleCacheRecord>>(rawJson)
                }.getOrDefault(emptyList()).map { record ->
                    MapStyleRecord(
                        id = record.id,
                        name = record.name,
                        source = MapStyleRecord.Source.REMOTE,
                        uri = record.uri,
                    )
                }
            }
        }

    @Serializable
    private data class RemoteStyleCacheRecord(
        val id: String,
        val name: String,
        val uri: String,
    )

    companion object {
        private val remoteStylesCache = stringPreferencesKey("remote_map_styles_cache")
    }
}
