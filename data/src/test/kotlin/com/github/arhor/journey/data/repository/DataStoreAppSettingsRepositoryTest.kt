package com.github.arhor.journey.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.error.AppSettingsError
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreAppSettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `observeSelectedMapStyle should emit cyberpunk when preference is not saved`() = runTest {
        // Given
        val repository = DataStoreAppSettingsRepository(createDataStore())

        // When
        val actual = repository.observeSelectedMapStyle().first()

        // Then
        actual shouldBe Output.Success(MapStyle.defaultStyle)
    }

    @Test
    fun `observeSelectedMapStyle should emit saved style when preference id is known`() = runTest {
        // Given
        val dataStore = createDataStore()
        val repository = DataStoreAppSettingsRepository(dataStore)

        // When
        repository.setSelectedMapStyle("light")
        val actual = repository.observeSelectedMapStyle().first()

        // Then
        actual shouldBe Output.Success(MapStyle.styleById("light")!!)
    }

    @Test
    fun `observeSelectedMapStyle should fallback to cyberpunk when preference id is unknown`() = runTest {
        // Given
        val dataStore = createDataStore()
        val repository = DataStoreAppSettingsRepository(dataStore)
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("selected_map_style_id")] = "unknown"
        }

        // When
        val actual = repository.observeSelectedMapStyle().first()

        // Then
        actual shouldBe Output.Success(MapStyle.defaultStyle)
    }

    @Test
    fun `setSelectedMapStyle should map write exceptions to settings failure`() = runTest {
        // Given
        val repository = DataStoreAppSettingsRepository(FailingWriteDataStore)

        // When
        val actual = repository.setSelectedMapStyle("light")

        // Then
        actual.shouldBeInstanceOf<Output.Failure<AppSettingsError>>()
        actual.error.shouldBeInstanceOf<AppSettingsError.SavingFailed>()
    }

    private fun TestScope.createDataStore(): DataStore<Preferences> {
        val preferencesFile = temporaryFolder.newFile(
            "${temporaryFolder.root.listFiles().orEmpty().size}.preferences_pb",
        )

        return PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { preferencesFile },
        )
    }

    private object FailingWriteDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences>
            get() = kotlinx.coroutines.flow.flowOf(emptyPreferences())

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            throw IllegalStateException("Write failed")
        }
    }
}
