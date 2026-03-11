package com.github.arhor.journey.ui.views.map

import com.github.arhor.journey.domain.exploration.model.DiscoveredPoi
import com.github.arhor.journey.domain.exploration.model.ExplorationProgress
import com.github.arhor.journey.domain.exploration.model.GeoPoint
import com.github.arhor.journey.domain.map.model.MapResolvedStyle
import com.github.arhor.journey.domain.map.model.MapStyle
import com.github.arhor.journey.domain.exploration.model.PoiCategory
import com.github.arhor.journey.domain.exploration.model.PointOfInterest
import com.github.arhor.journey.domain.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.ResolveMapStyleUseCase
import com.github.arhor.journey.test.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
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
    fun `initialize should expose mapped objects and selected style when all flows emit success`() = runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val selectedStyle = MutableSharedFlow<MapStyle>(replay = 1).apply { tryEmit(MapStyle(id = "dark", name = "Dark")) }
        val resolvedStyle = MutableSharedFlow<MapResolvedStyle>(replay = 1).apply { tryEmit(MapResolvedStyle.Uri("dark-style")) }
        val poiFlow = MutableSharedFlow<List<PointOfInterest>>(replay = 1).apply {
            tryEmit(listOf(pointOfInterest("poi-1", "Town Square"), pointOfInterest("poi-2", "Blacksmith")))
        }
        val explorationFlow = MutableSharedFlow<ExplorationProgress>(replay = 1).apply {
            tryEmit(ExplorationProgress(discovered = setOf(DiscoveredPoi("poi-2", Instant.parse("2026-01-01T00:00:00Z")))))
        }
        val fixture = createFixture(selectedStyle, resolvedStyle, poiFlow, explorationFlow)
        backgroundScope.launch { fixture.vm.uiState.collect() }

        // When
        advanceUntilIdle()

        // Then
        val state = fixture.vm.uiState.first { it is MapUiState.Content } as MapUiState.Content
        state.selectedStyle.id shouldBe "dark"
        state.resolvedStyle shouldBe MapResolvedStyle.Uri("dark-style")
        state.visibleObjects.map { it.id to it.isDiscovered } shouldBe listOf("poi-1" to false, "poi-2" to true)
    }

        private fun createFixture(
        selectedStyle: Flow<MapStyle>,
        resolvedStyle: Flow<MapResolvedStyle>,
        poiFlow: Flow<List<PointOfInterest>>,
        explorationFlow: Flow<ExplorationProgress>,
    ): Fixture {
        val observeSelectedMapStyle = mockk<ObserveSelectedMapStyleUseCase>()
        val observePointsOfInterest = mockk<ObservePointsOfInterestUseCase>()
        val observeExplorationProgress = mockk<ObserveExplorationProgressUseCase>()
        val discoverPointOfInterestUseCase = mockk<DiscoverPointOfInterestUseCase>()
        val resolveMapStyleUseCase = mockk<ResolveMapStyleUseCase>()

        every { observeSelectedMapStyle.invoke() } returns selectedStyle
        every { resolveMapStyleUseCase.invoke() } returns resolvedStyle
        every { observePointsOfInterest.invoke() } returns poiFlow
        every { observeExplorationProgress.invoke() } returns explorationFlow

        val vm = MapViewModel(
            observePointsOfInterest = observePointsOfInterest,
            observeExplorationProgress = observeExplorationProgress,
            observeSelectedMapStyle = observeSelectedMapStyle,
            discoverPointOfInterest = discoverPointOfInterestUseCase,
            resolveMapStyle = resolveMapStyleUseCase,
        )

        return Fixture(vm, discoverPointOfInterestUseCase)
    }

    private data class Fixture(
        val vm: MapViewModel,
        val discoverPointOfInterestUseCase: DiscoverPointOfInterestUseCase,
    )

    private fun pointOfInterest(id: String, name: String): PointOfInterest =
        PointOfInterest(
            id = id,
            name = name,
            description = "A point of interest",
            category = PoiCategory.LANDMARK,
            location = GeoPoint(37.7749, -122.4194),
            radiusMeters = 50,
        )
}
