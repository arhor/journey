package com.github.arhor.journey.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.arhor.journey.domain.settings.model.AppSettings
import com.github.arhor.journey.domain.settings.model.DistanceUnit
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

            AppSettings(distanceUnit = unit)
        }

    override suspend fun setDistanceUnit(unit: DistanceUnit) {
        dataStore.edit { prefs ->
            prefs[distanceUnit] = unit.name
        }
    }

    companion object {
        val distanceUnit = stringPreferencesKey("distance_unit")
    }
}
