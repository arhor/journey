package com.github.arhor.journey.ui.views.map

import com.github.arhor.journey.domain.exploration.model.ExplorationProgress
import com.github.arhor.journey.domain.map.model.MapStyle
import com.github.arhor.journey.domain.map.model.ResolvedMapStyle
import com.github.arhor.journey.domain.exploration.repository.ExplorationRepository
import com.github.arhor.journey.domain.map.repository.MapStylesRepository
import com.github.arhor.journey.domain.exploration.repository.PointOfInterestRepository
import com.github.arhor.journey.domain.settings.repository.SettingsRepository
import com.github.arhor.journey.domain.settings.model.AppSettings
import com.github.arhor.journey.domain.exploration.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.exploration.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.map.usecase.ObserveMapStylesUseCase
import com.github.arhor.journey.domain.exploration.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.map.usecase.ObserveSelectedStyleUseCase
import com.github.arhor.journey.domain.settings.usecase.ObserveSettingsUseCase
import com.github.arhor.journey.testutils.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Rule
import org.junit.Test
import java.time.Clock

class MapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `buildUiState should use default style when settings contain stale selection`() = mainDispatcherRule.runTest {
        // Given
        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.observeSettings() } returns flowOf(AppSettings(selectedMapStyleId = "missing"))

        val mapStylesRepository = mockk<MapStylesRepository>()
        every { mapStylesRepository.observeAvailableStyles() } returns flowOf(
            listOf(
                MapStyle(id = MapStyle.DEFAULT_ID, name = "Default"),
                MapStyle(id = "satellite", name = "Satellite"),
            ),
        )
        every { mapStylesRepository.observeSelectedStyle() } returns flowOf(
            ResolvedMapStyle.Uri("default-uri"),
        )

        val pointOfInterestRepository = mockk<PointOfInterestRepository>()
        every { pointOfInterestRepository.observeAll() } returns flowOf(emptyList())
        coEvery { pointOfInterestRepository.ensureSeeded() } returns Unit

        val explorationRepository = mockk<ExplorationRepository>()
        every { explorationRepository.observeProgress() } returns flowOf(ExplorationProgress(emptySet()))

        val viewModel = MapViewModel(
            observePointsOfInterest = ObservePointsOfInterestUseCase(pointOfInterestRepository),
            observeExplorationProgress = ObserveExplorationProgressUseCase(explorationRepository),
            observeSettings = ObserveSettingsUseCase(settingsRepository),
            observeAvailableMapStyles = ObserveMapStylesUseCase(mapStylesRepository),
            discoverPointOfInterest = DiscoverPointOfInterestUseCase(explorationRepository, Clock.systemUTC()),
            observeSelectedStyle = ObserveSelectedStyleUseCase(mapStylesRepository),
        )

        val collectionJob = backgroundScope.launch(mainDispatcherRule) { viewModel.uiState.collect() }

        // When
        advanceUntilIdle()
        val actual = viewModel.uiState.value.shouldBeInstanceOf<MapUiState.Content>()

        // Then
        actual.selectedStyle shouldBe MapStyle(id = MapStyle.DEFAULT_ID, name = "Default")
        actual.resolvedStyle shouldBe ResolvedMapStyle.Uri("default-uri")
        collectionJob.cancel()
    }
}
