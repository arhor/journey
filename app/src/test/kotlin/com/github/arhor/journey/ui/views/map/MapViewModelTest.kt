package com.github.arhor.journey.ui.views.map

import com.github.arhor.journey.core.logging.NoOpLoggerFactory
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.DiscoveredPoi
import com.github.arhor.journey.domain.model.ExplorationProgress
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.PoiCategory
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveSettingsUseCase
import com.github.arhor.journey.test.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
        val settingsFlow = MutableSharedFlow<AppSettings>(replay = 1).apply {
            tryEmit(AppSettings(mapStyle = MapStyle.DARK))
        }
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
        val fixture = createFixture(settingsFlow, poiFlow, explorationFlow)
        every { fixture.mapStyleRepository.resolve(MapStyle.DARK) } returns MapResolvedStyle.Uri("dark-style")
        backgroundScope.launch { fixture.vm.uiState.collect() }

        // When
        advanceUntilIdle()

        // Then
        val state = fixture.vm.uiState.first { !it.isLoading }
        state.selectedStyle shouldBe MapStyle.DARK
        state.resolvedStyle shouldBe MapResolvedStyle.Uri("dark-style")
        state.visibleObjects.map { it.id to it.isDiscovered } shouldBe listOf("poi-1" to false, "poi-2" to true)
    }

    @Test
    fun `initialize should fallback to default style when settings fail`() = runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val settingsFlow = flow<AppSettings> {
            throw IllegalStateException("Settings unavailable")
        }
        val poiFlow = MutableSharedFlow<List<PointOfInterest>>(replay = 1).apply {
            tryEmit(emptyList())
        }
        val explorationFlow = MutableSharedFlow<ExplorationProgress>(replay = 1).apply {
            tryEmit(ExplorationProgress(discovered = emptySet()))
        }
        val fixture = createFixture(settingsFlow, poiFlow, explorationFlow)
        backgroundScope.launch { fixture.vm.uiState.collect() }

        // When
        advanceUntilIdle()

        // Then
        fixture.vm.uiState.value.selectedStyle shouldBe MapStyle.DEFAULT
        fixture.vm.uiState.value.resolvedStyle shouldBe MapResolvedStyle.Uri(MapStyleRepository.DEFAULT_STYLE_FALLBACK_URI)
    }

    @Test
    fun `on object tapped should discover poi and open details when discovery succeeds`() = runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val settingsFlow = MutableSharedFlow<AppSettings>(replay = 1).apply {
            tryEmit(AppSettings(mapStyle = MapStyle.DEFAULT))
        }
        val poiFlow = MutableSharedFlow<List<PointOfInterest>>(replay = 1).apply {
            tryEmit(listOf(pointOfInterest(id = "poi-1", name = "Town Square")))
        }
        val explorationFlow = MutableSharedFlow<ExplorationProgress>(replay = 1).apply {
            tryEmit(ExplorationProgress(discovered = emptySet()))
        }
        val fixture = createFixture(settingsFlow, poiFlow, explorationFlow)
        coEvery { fixture.discoverPointOfInterestUseCase.invoke(any()) } returns Unit
        every { fixture.mapStyleRepository.resolve(MapStyle.DEFAULT) } returns MapResolvedStyle.Uri("default-style")
        backgroundScope.launch { fixture.vm.uiState.collect() }
        advanceUntilIdle()
        val effect = async { fixture.vm.effects.first() }

        // When
        fixture.vm.dispatch(MapIntent.OnObjectTapped("poi-1"))
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { fixture.discoverPointOfInterestUseCase.invoke("poi-1") }
        effect.await() shouldBe MapEffect.OpenObjectDetails("poi-1")
    }

    @Test
    fun `on map load failed should expose error and clear it when retry is dispatched`() = runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val settingsFlow = MutableSharedFlow<AppSettings>(replay = 1).apply {
            tryEmit(AppSettings(mapStyle = MapStyle.DEFAULT))
        }
        val poiFlow = MutableSharedFlow<List<PointOfInterest>>(replay = 1).apply {
            tryEmit(emptyList())
        }
        val explorationFlow = MutableSharedFlow<ExplorationProgress>(replay = 1).apply {
            tryEmit(ExplorationProgress(discovered = emptySet()))
        }
        val fixture = createFixture(settingsFlow, poiFlow, explorationFlow)
        every { fixture.mapStyleRepository.resolve(MapStyle.DEFAULT) } returns MapResolvedStyle.Uri("default-style")
        backgroundScope.launch { fixture.vm.uiState.collect() }
        advanceUntilIdle()

        // When
        fixture.vm.dispatch(MapIntent.OnMapLoadFailed())
        advanceUntilIdle()

        // Then
        fixture.vm.uiState.value.styleLoadErrorMessage shouldBe "Failed to load map style."

        // When
        fixture.vm.dispatch(MapIntent.RetryStyleLoad)
        advanceUntilIdle()

        // Then
        fixture.vm.uiState.value.styleLoadErrorMessage shouldBe null
        fixture.vm.uiState.value.styleReloadToken shouldBe 1
    }

    private fun createFixture(
        settingsFlow: Flow<AppSettings>,
        poiFlow: Flow<List<PointOfInterest>>,
        explorationFlow: Flow<ExplorationProgress>,
    ): Fixture {
        val observeSettingsUseCase = mockk<ObserveSettingsUseCase>()
        val observePointsOfInterestUseCase = mockk<ObservePointsOfInterestUseCase>()
        val observeExplorationProgressUseCase = mockk<ObserveExplorationProgressUseCase>()
        val discoverPointOfInterestUseCase = mockk<DiscoverPointOfInterestUseCase>()
        val mapStyleRepository = mockk<MapStyleRepository>()

        every { observeSettingsUseCase.invoke() } returns settingsFlow
        every { observePointsOfInterestUseCase.invoke() } returns poiFlow
        every { observeExplorationProgressUseCase.invoke() } returns explorationFlow

        val vm = MapViewModel(
            observePointsOfInterest = observePointsOfInterestUseCase,
            observeExplorationProgress = observeExplorationProgressUseCase,
            observeSettings = observeSettingsUseCase,
            discoverPointOfInterest = discoverPointOfInterestUseCase,
            mapStyleRepository = mapStyleRepository,
            loggerFactory = NoOpLoggerFactory,
        )

        return Fixture(vm, discoverPointOfInterestUseCase, mapStyleRepository)
    }

    private data class Fixture(
        val vm: MapViewModel,
        val discoverPointOfInterestUseCase: DiscoverPointOfInterestUseCase,
        val mapStyleRepository: MapStyleRepository,
    )

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
