package com.github.arhor.journey.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.github.arhor.journey.domain.model.MapStyle
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsRepositoryImplTest {

    @Test
    fun `observeSettings should return persisted map style when value is valid`() = runTest {
        // Given
        val file = File.createTempFile("settings", ".preferences_pb")
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            produceFile = { file.toPath().toOkioPath() },
        )
        val repository = DataStoreSettingsRepositoryImpl(dataStore)

        // When
        repository.setMapStyle(MapStyle.TERRAIN)
        val settings = repository.observeSettings().first()

        // Then
        settings.mapStyle shouldBe MapStyle.TERRAIN
    }

    @Test
    fun `observeSettings should fallback to default when persisted map style is invalid`() = runTest {
        // Given
        val file = File.createTempFile("settings", ".preferences_pb")
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            produceFile = { file.toPath().toOkioPath() },
        )
        val repository = DataStoreSettingsRepositoryImpl(dataStore)
        dataStore.edit { prefs ->
            prefs[DataStoreSettingsRepositoryImpl.selectedMapStyle] = "INVALID"
        }

        // When
        val settings = repository.observeSettings().first()

        // Then
        settings.mapStyle shouldBe MapStyle.DEFAULT
    }
}
