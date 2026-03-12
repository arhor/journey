package com.github.arhor.journey.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.DistanceUnit
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class DataStoreSettingsRepositoryImplTest {

    @Test
    fun `observeSettings should emit distance unit and selected map style when both preferences are stored`() = runTest {
        // Given
        val dataStore = createDataStore(backgroundScope)
        val repository = DataStoreSettingsRepositoryImpl(dataStore)

        repository.setDistanceUnit(DistanceUnit.IMPERIAL)
        repository.setSelectedMapStyleId("satellite")

        // When
        val actual = repository.observeSettings().first()

        // Then
        actual shouldBe AppSettings(
            distanceUnit = DistanceUnit.IMPERIAL,
            selectedMapStyleId = "satellite",
        )
    }

    @Test
    fun `setSelectedMapStyleId should persist value under selected map style key when selection changes`() = runTest {
        // Given
        val dataStore = createDataStore(backgroundScope)
        val repository = DataStoreSettingsRepositoryImpl(dataStore)

        // When
        repository.setSelectedMapStyleId("terrain")

        // Then
        val preferences = dataStore.data.first()
        preferences[DataStoreSettingsRepositoryImpl.selectedMapStyleId] shouldBe "terrain"
    }

    private fun createDataStore(scope: CoroutineScope): androidx.datastore.core.DataStore<Preferences> {
        val tempDirectory = createTempDirectory(prefix = "settings-repository-test-").toFile()
        val dataStoreFile = File(tempDirectory, "settings.preferences_pb")

        return PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { dataStoreFile },
        )
    }
}
