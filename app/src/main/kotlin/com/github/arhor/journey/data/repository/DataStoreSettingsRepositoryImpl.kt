package com.github.arhor.journey.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun observeSettings(): Flow<AppSettings> =
        dataStore.data.map { prefs ->
            val unit = prefs[distanceUnit]
                ?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() }
                ?: DistanceUnit.METRIC
            val mapStyle = MapStyle.fromName(prefs[selectedMapStyle])

            AppSettings(
                distanceUnit = unit,
                mapStyle = mapStyle,
            )
        }

    override suspend fun setDistanceUnit(unit: DistanceUnit) {
        dataStore.edit { prefs ->
            prefs[distanceUnit] = unit.name
        }
    }

    override suspend fun setMapStyle(style: MapStyle) {
        dataStore.edit { prefs ->
            prefs[selectedMapStyle] = style.name
        }
    }

    companion object {
        val distanceUnit = stringPreferencesKey("distance_unit")
        val selectedMapStyle = stringPreferencesKey("map_style")
    }
}
