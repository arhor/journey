package com.github.arhor.journey.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.arhor.journey.domain.map.model.MapStyle
import com.github.arhor.journey.domain.repository.SettingsRepository
import com.github.arhor.journey.domain.settings.model.AppSettings
import com.github.arhor.journey.domain.settings.model.DistanceUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun observeSettings(): Flow<AppSettings> =
        dataStore.data.map {
            AppSettings(
                distanceUnit = it.distanceUnitPref,
                selectedMapStyleId = it.selectedMapStyleIdPref,
            )
        }

    override suspend fun setDistanceUnit(unit: DistanceUnit) {
        dataStore.edit {
            it[distanceUnit] = unit.name
        }
    }

    override suspend fun setSelectedMapStyleId(styleId: String) {
        dataStore.edit {
            it[selectedMapStyleId] = styleId
        }
    }

    private val Preferences.distanceUnitPref: DistanceUnit
        get() = this[distanceUnit]?.let(DistanceUnit::fromString) ?: DistanceUnit.METRIC

    private val Preferences.selectedMapStyleIdPref: String
        get() = this[selectedMapStyleId] ?: MapStyle.DEFAULT_ID

    companion object {
        val distanceUnit = stringPreferencesKey("distance_unit")
        val selectedMapStyleId = stringPreferencesKey("selected_map_style_id")
    }
}
