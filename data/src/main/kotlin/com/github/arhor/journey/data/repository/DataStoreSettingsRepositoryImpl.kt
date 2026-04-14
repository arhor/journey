package com.github.arhor.journey.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.toOutputFlow
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.error.AppSettingsError
import com.github.arhor.journey.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun observeSettings(): Flow<Output<AppSettings, AppSettingsError>> =
        dataStore.data.toOutputFlow(
            onSuccess = {
                AppSettings(
                    selectedMapStyleId = it.selectedMapStyleIdPref,
                )
            },
            onFailure = {
                AppSettingsError.LoadingFailed(
                    cause = it,
                )
            },
        )

    override suspend fun setSelectedMapStyleId(styleId: String) {
        dataStore.edit {
            it[selectedMapStyleId] = styleId
        }
    }

    private val Preferences.selectedMapStyleIdPref: String?
        get() = this[selectedMapStyleId]

    companion object {
        val selectedMapStyleId = stringPreferencesKey("selected_map_style_id")
    }
}
