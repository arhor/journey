package com.github.arhor.journey.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
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
    fun `observeSettings should return persisted distance unit when value is valid`() = runTest {
        // Given
        val file = File.createTempFile("settings", ".preferences_pb")
        val dataStore = PreferenceDataStoreFactory.createWithPath(produceFile = { file.toPath().toOkioPath() })
        val repository = DataStoreSettingsRepositoryImpl(dataStore)

        // When
        repository.setDistanceUnit(com.github.arhor.journey.domain.model.DistanceUnit.IMPERIAL)
        val settings = repository.observeSettings().first()

        // Then
        settings.distanceUnit shouldBe com.github.arhor.journey.domain.model.DistanceUnit.IMPERIAL
    }

    @Test
    fun `observeSettings should fallback to metric when persisted distance unit is invalid`() = runTest {
        // Given
        val file = File.createTempFile("settings", ".preferences_pb")
        val dataStore = PreferenceDataStoreFactory.createWithPath(produceFile = { file.toPath().toOkioPath() })
        val repository = DataStoreSettingsRepositoryImpl(dataStore)
        dataStore.edit { prefs -> prefs[DataStoreSettingsRepositoryImpl.distanceUnit] = "INVALID" }

        // When
        val settings = repository.observeSettings().first()

        // Then
        settings.distanceUnit shouldBe com.github.arhor.journey.domain.model.DistanceUnit.METRIC
    }
}
