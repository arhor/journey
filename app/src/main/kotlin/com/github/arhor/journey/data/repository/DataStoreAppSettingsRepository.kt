package com.github.arhor.journey.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.error.AppSettingsError
import com.github.arhor.journey.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreAppSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppSettingsRepository {

    override fun observeAvailableMapStyles(): Flow<Output<List<MapStyle>, AppSettingsError>> =
        flowOf(Output.Success(MapStyle.availableStyles))

    override fun observeSelectedMapStyle(): Flow<Output<MapStyle, AppSettingsError>> =
        dataStore.data
            .map<Preferences, Output<MapStyle, AppSettingsError>> { preferences ->
                Output.Success(
                    preferences[SELECTED_MAP_STYLE_ID]
                        ?.let(MapStyle::styleById)
                        ?: MapStyle.defaultStyle,
                )
            }
            .catch { error ->
                emit(
                    Output.Failure(
                        AppSettingsError.LoadingFailed(
                            cause = error,
                            message = error.message,
                        ),
                    ),
                )
            }

    override suspend fun setSelectedMapStyle(styleId: String): Output<Unit, AppSettingsError> =
        runCatching {
            dataStore.edit { preferences ->
                preferences[SELECTED_MAP_STYLE_ID] = styleId
            }
        }.fold(
            onSuccess = { Output.Success(Unit) },
            onFailure = { error ->
                Output.Failure(
                    AppSettingsError.SavingFailed(
                        cause = error,
                        message = error.message,
                    ),
                )
            },
        )

    private companion object {
        val SELECTED_MAP_STYLE_ID = stringPreferencesKey("selected_map_style_id")
    }
}
