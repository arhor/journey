package com.github.arhor.journey.ui.views.map

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.ExplorationProgress
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.usecase.GetAllMapStylesUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveSettingsUseCase
import com.github.arhor.journey.testing.MainDispatcherRule
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class MapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val observePointsOfInterest = mockk<ObservePointsOfInterestUseCase>()
    private val observeExplorationProgress = mockk<ObserveExplorationProgressUseCase>()
    private val observeSettings = mockk<ObserveSettingsUseCase>()
    private val getAllMapStyles = mockk<GetAllMapStylesUseCase>()
    private val discoverPointOfInterest = mockk<DiscoverPointOfInterestUseCase>()

    @Test
    fun `recenter click should emit permission request effect when current location is requested`() = runTest {
        // Given
        stubDependencies()
        val viewModel = createViewModel()
        val effects = mutableListOf<MapEffect>()
        val collectJob = collectEffects(viewModel, effects)
        val uiStateJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        advanceUntilIdle()
        val initialState = viewModel.uiState.value as MapUiState.Content

        // When
        viewModel.dispatch(MapIntent.RecenterClicked)
        advanceUntilIdle()

        // Then
        effects shouldContainExactly listOf(MapEffect.RequestLocationPermission)
        viewModel.uiState.value shouldBe initialState
        collectJob.cancel()
        uiStateJob.cancel()
    }

    @Test
    fun `location permission result should emit recenter effect when permission is granted after recenter click`() =
        runTest {
        // Given
        stubDependencies()
        val viewModel = createViewModel()
        val effects = mutableListOf<MapEffect>()
        val collectJob = collectEffects(viewModel, effects)
        advanceUntilIdle()

        // When
        viewModel.dispatch(MapIntent.RecenterClicked)
        viewModel.dispatch(MapIntent.LocationPermissionResult(isGranted = true))
        advanceUntilIdle()

        // Then
        effects shouldContainExactly listOf(
            MapEffect.RequestLocationPermission,
            MapEffect.RecenterOnCurrentLocation,
        )
        collectJob.cancel()
    }

    @Test
    fun `location permission result should show a message when permission is denied after recenter click`() = runTest {
        // Given
        stubDependencies()
        val viewModel = createViewModel()
        val effects = mutableListOf<MapEffect>()
        val collectJob = collectEffects(viewModel, effects)
        advanceUntilIdle()

        // When
        viewModel.dispatch(MapIntent.RecenterClicked)
        viewModel.dispatch(MapIntent.LocationPermissionResult(isGranted = false))
        advanceUntilIdle()

        // Then
        effects shouldContainExactly listOf(
            MapEffect.RequestLocationPermission,
            MapEffect.ShowMessage(
                message = "Location permission is required to center the map on your position.",
            ),
        )
        collectJob.cancel()
    }

    @Test
    fun `current location unavailable should show a message when recenter cannot resolve a location`() = runTest {
        // Given
        stubDependencies()
        val viewModel = createViewModel()
        val effects = mutableListOf<MapEffect>()
        val collectJob = collectEffects(viewModel, effects)
        advanceUntilIdle()

        // When
        viewModel.dispatch(MapIntent.CurrentLocationUnavailable)
        advanceUntilIdle()

        // Then
        effects shouldContainExactly listOf(
            MapEffect.ShowMessage(
                message = "Current location is not available yet.",
            ),
        )
        collectJob.cancel()
    }

    @Test
    fun `location permission result should do nothing when there is no pending recenter request`() = runTest {
        // Given
        stubDependencies()
        val viewModel = createViewModel()
        val effects = mutableListOf<MapEffect>()
        val collectJob = collectEffects(viewModel, effects)
        advanceUntilIdle()

        // When
        viewModel.dispatch(MapIntent.LocationPermissionResult(isGranted = true))
        advanceUntilIdle()

        // Then
        effects shouldBe emptyList()
        collectJob.cancel()
    }

    private fun stubDependencies() {
        every { observePointsOfInterest() } returns flowOf(emptyList())
        every { observeExplorationProgress() } returns flowOf(ExplorationProgress(discovered = emptySet()))
        every { observeSettings() } returns flowOf(AppSettings(selectedMapStyleId = null))
        every { getAllMapStyles() } returns MutableStateFlow(
            Output.Success(
                listOf(
                    MapStyle.remote(
                        id = "default",
                        name = "Default",
                        value = "https://example.com/style.json",
                    ),
                ),
            ),
        )
        coJustRun { discoverPointOfInterest(any()) }
    }

    private fun createViewModel(): MapViewModel =
        MapViewModel(
            observePointsOfInterest = observePointsOfInterest,
            observeExplorationProgress = observeExplorationProgress,
            observeSettings = observeSettings,
            getAllMapStyles = getAllMapStyles,
            discoverPointOfInterest = discoverPointOfInterest,
        )

    private fun TestScope.collectEffects(
        viewModel: MapViewModel,
        effects: MutableList<MapEffect>,
    ): Job = launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.effects.collect(effects::add)
    }
}
