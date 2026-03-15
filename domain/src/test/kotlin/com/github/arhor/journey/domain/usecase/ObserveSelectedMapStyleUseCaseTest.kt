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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveSelectedMapStyleUseCaseTest {

    @Test
    fun `invoke should emit selected map style when settings contain existing style id`() = runTest {
        // Given
        val selectedStyle = MapStyle.remote(
            id = "remote-sat",
            name = "Satellite",
            value = "https://styles/sat.json",
        )
        val settingsRepository = FakeSettingsRepository(
            Output.Success(AppSettings(distanceUnit = DistanceUnit.METRIC, selectedMapStyleId = "remote-sat")),
        )
        val mapStylesRepository = FakeMapStylesRepository(
            Output.Success(
                listOf(
                    MapStyle.bundle(id = "bundle-default", name = "Default", value = "map/default.json"),
                    selectedStyle,
                ),
            ),
        )
        val subject = ObserveSelectedMapStyleUseCase(
            settingsRepository = settingsRepository,
            mapStylesRepository = mapStylesRepository,
        )

        // When
        val result = subject().first()

        // Then
        result shouldBe Output.Success(selectedStyle)
    }

    @Test
    fun `invoke should emit null when settings reference missing map style id`() = runTest {
        // Given
        val settingsRepository = FakeSettingsRepository(
            Output.Success(AppSettings(distanceUnit = DistanceUnit.IMPERIAL, selectedMapStyleId = "missing")),
        )
        val mapStylesRepository = FakeMapStylesRepository(
            Output.Success(
                listOf(MapStyle.bundle(id = "bundle-default", name = "Default", value = "map/default.json")),
            ),
        )
        val subject = ObserveSelectedMapStyleUseCase(
            settingsRepository = settingsRepository,
            mapStylesRepository = mapStylesRepository,
        )

        // When
        val result = subject().first()

        // Then
        result shouldBe Output.Success(null)
    }

    @Test
    fun `invoke should emit settings failure when settings flow fails`() = runTest {
        // Given
        val expectedError = AppSettingsError.LoadingFailed(message = "settings failed")
        val settingsRepository = FakeSettingsRepository(Output.Failure(expectedError))
        val mapStylesRepository = FakeMapStylesRepository(
            Output.Failure(MapStylesError.UnknownFailure(cause = IllegalStateException("styles failed"))),
        )
        val subject = ObserveSelectedMapStyleUseCase(
            settingsRepository = settingsRepository,
            mapStylesRepository = mapStylesRepository,
        )

        // When
        val result = subject().first()

        // Then
        result shouldBe Output.Failure(expectedError)
    }

    private class FakeSettingsRepository(
        value: Output<AppSettings, AppSettingsError>,
    ) : SettingsRepository {
        private val state = MutableStateFlow(value)

        override fun observeSettings(): Flow<Output<AppSettings, AppSettingsError>> = state

        override suspend fun setDistanceUnit(unit: DistanceUnit) = Unit

        override suspend fun setSelectedMapStyleId(styleId: String) = Unit
    }

    private class FakeMapStylesRepository(
        value: Output<List<MapStyle>, MapStylesError>,
    ) : MapStylesRepository {
        private val state = MutableStateFlow(value)

        override fun observeMapStyles(): MutableStateFlow<Output<List<MapStyle>, MapStylesError>> = state
    }
}
