package com.github.arhor.journey.ui.views.map

import com.github.arhor.journey.core.logging.NoOpLoggerFactory
import com.github.arhor.journey.domain.model.DiscoveredPoi
import com.github.arhor.journey.domain.model.ExplorationProgress
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.PoiCategory
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.test.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initialize should expose mapped map objects when poi and exploration flows emit`() = runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val poiFlow = MutableSharedFlow<List<PointOfInterest>>(replay = 1).apply {
            tryEmit(
                listOf(
                    pointOfInterest(id = "poi-1", name = "Town Square"),
                    pointOfInterest(id = "poi-2", name = "Blacksmith"),
                ),
            )
        }
        val explorationFlow = MutableSharedFlow<ExplorationProgress>(replay = 1).apply {
            tryEmit(
                ExplorationProgress(
                    discovered = setOf(
                        DiscoveredPoi(
                            poiId = "poi-2",
                            discoveredAt = Instant.parse("2026-01-01T00:00:00Z"),
                        ),
                    ),
                ),
            )
        }
        val observePointsOfInterestUseCase = mockk<ObservePointsOfInterestUseCase>()
        val observeExplorationProgressUseCase = mockk<ObserveExplorationProgressUseCase>()
        val discoverPointOfInterestUseCase = mockk<DiscoverPointOfInterestUseCase>()
        every { observePointsOfInterestUseCase.invoke() } returns poiFlow
        every { observeExplorationProgressUseCase.invoke() } returns explorationFlow

        val vm = MapViewModel(
            observePointsOfInterest = observePointsOfInterestUseCase,
            observeExplorationProgress = observeExplorationProgressUseCase,
            discoverPointOfInterest = discoverPointOfInterestUseCase,
            loggerFactory = NoOpLoggerFactory,
        )
        backgroundScope.launch { vm.uiState.collect() }

        // When
        advanceUntilIdle()

        // Then
        vm.uiState.value.isLoading shouldBe false
        vm.uiState.value.errorMessage shouldBe null
        vm.uiState.value.visibleObjects shouldBe listOf(
            MapObjectUiModel(
                id = "poi-1",
                title = "Town Square",
                description = "A point of interest",
                position = LatLng(latitude = 37.7749, longitude = -122.4194),
                radiusMeters = 50,
                isDiscovered = false,
            ),
            MapObjectUiModel(
                id = "poi-2",
                title = "Blacksmith",
                description = "A point of interest",
                position = LatLng(latitude = 37.7749, longitude = -122.4194),
                radiusMeters = 50,
                isDiscovered = true,
            ),
        )
    }

    @Test
    fun `on object tapped should discover poi and open details when discovery succeeds`() = runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val poiFlow = MutableSharedFlow<List<PointOfInterest>>(replay = 1).apply {
            tryEmit(listOf(pointOfInterest(id = "poi-1", name = "Town Square")))
        }
        val explorationFlow = MutableSharedFlow<ExplorationProgress>(replay = 1).apply {
            tryEmit(ExplorationProgress(discovered = emptySet()))
        }
        val observePointsOfInterestUseCase = mockk<ObservePointsOfInterestUseCase>()
        val observeExplorationProgressUseCase = mockk<ObserveExplorationProgressUseCase>()
        val discoverPointOfInterestUseCase = mockk<DiscoverPointOfInterestUseCase>()
        every { observePointsOfInterestUseCase.invoke() } returns poiFlow
        every { observeExplorationProgressUseCase.invoke() } returns explorationFlow
        coEvery { discoverPointOfInterestUseCase.invoke(any()) } returns Unit

        val vm = MapViewModel(
            observePointsOfInterest = observePointsOfInterestUseCase,
            observeExplorationProgress = observeExplorationProgressUseCase,
            discoverPointOfInterest = discoverPointOfInterestUseCase,
            loggerFactory = NoOpLoggerFactory,
        )
        backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        val effect = async { vm.effects.first() }

        // When
        vm.dispatch(MapIntent.OnObjectTapped(objectId = "poi-1"))
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { discoverPointOfInterestUseCase.invoke("poi-1") }
        effect.await() shouldBe MapEffect.OpenObjectDetails(objectId = "poi-1")
    }

    @Test
    fun `on recenter clicked should request location permission when intent is dispatched`() = runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val poiFlow = MutableSharedFlow<List<PointOfInterest>>(replay = 1).apply {
            tryEmit(emptyList())
        }
        val explorationFlow = MutableSharedFlow<ExplorationProgress>(replay = 1).apply {
            tryEmit(ExplorationProgress(discovered = emptySet()))
        }
        val observePointsOfInterestUseCase = mockk<ObservePointsOfInterestUseCase>()
        val observeExplorationProgressUseCase = mockk<ObserveExplorationProgressUseCase>()
        val discoverPointOfInterestUseCase = mockk<DiscoverPointOfInterestUseCase>()
        every { observePointsOfInterestUseCase.invoke() } returns poiFlow
        every { observeExplorationProgressUseCase.invoke() } returns explorationFlow

        val vm = MapViewModel(
            observePointsOfInterest = observePointsOfInterestUseCase,
            observeExplorationProgress = observeExplorationProgressUseCase,
            discoverPointOfInterest = discoverPointOfInterestUseCase,
            loggerFactory = NoOpLoggerFactory,
        )
        backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        val effect = async { vm.effects.first() }

        // When
        vm.dispatch(MapIntent.OnRecenterClicked)
        advanceUntilIdle()

        // Then
        effect.await() shouldBe MapEffect.RequestLocationPermission
    }

    private fun pointOfInterest(id: String, name: String): PointOfInterest =
        PointOfInterest(
            id = id,
            name = name,
            description = "A point of interest",
            category = PoiCategory.LANDMARK,
            location = GeoPoint(lat = 37.7749, lon = -122.4194),
            radiusMeters = 50,
        )
}
