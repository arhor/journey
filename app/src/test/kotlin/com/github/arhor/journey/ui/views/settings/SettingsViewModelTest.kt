package com.github.arhor.journey.ui.views.settings

import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.repository.MapStylesRepository
import com.github.arhor.journey.domain.repository.SettingsRepository
import com.github.arhor.journey.domain.usecase.ObserveMapStylesUseCase
import com.github.arhor.journey.domain.usecase.ObserveSettingsUseCase
import com.github.arhor.journey.domain.usecase.SetDistanceUnitUseCase
import com.github.arhor.journey.domain.usecase.SetMapStyleUseCase
import com.github.arhor.journey.testutils.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `buildUiState should expose default selected style id when settings contain stale selection`() =
        mainDispatcherRule.runTest {
            // Given
            val settingsRepository = mockk<SettingsRepository>(relaxed = true) {
                every { observeSettings() } returns flowOf(
                    AppSettings(
                        distanceUnit = DistanceUnit.IMPERIAL,
                        selectedMapStyleId = "missing",
                    ),
                )
            }
            val mapStylesRepository = mockk<MapStylesRepository>(relaxed = true) {
                every { observeAvailableStyles() } returns flowOf(
                    listOf(
                        MapStyle(id = MapStyle.DEFAULT_ID, name = "Default"),
                        MapStyle(id = "satellite", name = "Satellite"),
                    ),
                )
            }

            val viewModel = SettingsViewModel(
                observeSettings = ObserveSettingsUseCase(settingsRepository),
                observeMapStyles = ObserveMapStylesUseCase(mapStylesRepository),
                setDistanceUnit = SetDistanceUnitUseCase(settingsRepository),
                setMapStyle = SetMapStyleUseCase(mapStylesRepository, settingsRepository),
            )

            val collectionJob = backgroundScope.launch(mainDispatcherRule) { viewModel.uiState.collect() }

            // When
            advanceUntilIdle()
            val actual = viewModel.uiState.value.shouldBeInstanceOf<SettingsUiState.Content>()

            // Then
            actual.distanceUnit shouldBe DistanceUnit.IMPERIAL
            actual.selectedMapStyleId shouldBe MapStyle.DEFAULT_ID
            actual.availableMapStyles shouldBe listOf(
                MapStyle(id = MapStyle.DEFAULT_ID, name = "Default"),
                MapStyle(id = "satellite", name = "Satellite"),
            )
            collectionJob.cancel()
        }
}
