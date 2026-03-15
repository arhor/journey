package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.error.AppSettingsError
import com.github.arhor.journey.domain.model.error.MapStylesError
import com.github.arhor.journey.domain.repository.MapStylesRepository
import com.github.arhor.journey.domain.repository.SettingsRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SetMapStyleUseCaseTest {

    @Test
    fun `invoke should persist selected style id when map style exists`() = runTest {
        // Given
        val styles = listOf(
            MapStyle.bundle(id = "bundle-default", name = "Default", value = "map/default.json"),
            MapStyle.remote(id = "remote-sat", name = "Satellite", value = "https://styles/sat.json"),
        )
        val settingsRepository = FakeSettingsRepository()
        val mapStylesRepository = FakeMapStylesRepository(Output.Success(styles))
        val subject = SetMapStyleUseCase(settingsRepository = settingsRepository, mapStylesRepository = mapStylesRepository)

        // When
        subject("remote-sat")

        // Then
        settingsRepository.selectedStyleIds shouldBe listOf("remote-sat")
    }

    @Test
    fun `invoke should not persist style id when style is missing from available map styles`() = runTest {
        // Given
        val styles = listOf(MapStyle.bundle(id = "bundle-default", name = "Default", value = "map/default.json"))
        val settingsRepository = FakeSettingsRepository()
        val mapStylesRepository = FakeMapStylesRepository(Output.Success(styles))
        val subject = SetMapStyleUseCase(settingsRepository = settingsRepository, mapStylesRepository = mapStylesRepository)

        // When
        subject("remote-sat")

        // Then
        settingsRepository.selectedStyleIds shouldBe emptyList()
    }

    @Test
    fun `invoke should not persist style id when map styles stream is in failure state`() = runTest {
        // Given
        val error = MapStylesError.UnknownFailure(cause = IllegalStateException("failed to load styles"))
        val settingsRepository = FakeSettingsRepository()
        val mapStylesRepository = FakeMapStylesRepository(Output.Failure(error))
        val subject = SetMapStyleUseCase(settingsRepository = settingsRepository, mapStylesRepository = mapStylesRepository)

        // When
        subject("remote-sat")

        // Then
        settingsRepository.selectedStyleIds shouldBe emptyList()
    }

    private class FakeMapStylesRepository(
        value: Output<List<MapStyle>, MapStylesError>,
    ) : MapStylesRepository {
        private val state = MutableStateFlow(value)

        override fun observeMapStyles(): MutableStateFlow<Output<List<MapStyle>, MapStylesError>> = state
    }

    private class FakeSettingsRepository : SettingsRepository {
        val selectedStyleIds: MutableList<String> = mutableListOf()

        override fun observeSettings(): Flow<Output<AppSettings, AppSettingsError>> =
            flowOf(Output.Success(AppSettings(distanceUnit = DistanceUnit.METRIC, selectedMapStyleId = null)))

        override suspend fun setDistanceUnit(unit: DistanceUnit) = Unit

        override suspend fun setSelectedMapStyleId(styleId: String) {
            selectedStyleIds += styleId
        }
    }
}
