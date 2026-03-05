package com.github.arhor.journey.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.arhor.journey.data.local.preferences.SettingsKeys
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun observeSettings(): Flow<AppSettings> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
            val unit = prefs[SettingsKeys.distanceUnit]
                ?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() }
                ?: DistanceUnit.METRIC

            AppSettings(distanceUnit = unit)
        }

    override suspend fun setDistanceUnit(unit: DistanceUnit) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.distanceUnit] = unit.name
        }
    }
}
