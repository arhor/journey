@file:Suppress("UnusedFlow")

package com.github.arhor.journey.feature.map

import androidx.lifecycle.viewModelScope
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.CANONICAL_ZOOM
import com.github.arhor.journey.domain.internal.bounds
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.error.StartExplorationTrackingSessionError
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.AppSettingsRepository
import com.github.arhor.journey.domain.usecase.GetExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.GetPackedExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveCollectibleResourceSpawnsUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObservePackedExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.StartExplorationTrackingSessionUseCase
import com.github.arhor.journey.feature.map.fow.FogOfWarCalculator
import com.github.arhor.journey.feature.map.fow.FogOfWarController
import com.github.arhor.journey.feature.map.fow.FowRenderDataFactory
import com.github.arhor.journey.feature.map.presentation.MapWorldObjectPresenter
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.AfterClass
import org.junit.Test

class MapViewModelTest {

    @Test
    fun `uiState should expose selected map style uri`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            selectedMapStyle = MapStyle.styleById("light")!!,
        )

        try {
            // When
            val actual = fixture.viewModel.awaitContent()

            // Then
            actual.mapStyleUri shouldBe "asset://map/styles/light.json"
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should include resource spawns when query window contains them`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            resourceSpawns = listOf(
                resourceSpawn(
                    id = "cell-1-slot-0",
                    resourceTypeId = "scrap",
                    lat = 50.46,
                    lon = 30.53,
                    collectionRadiusMeters = 24.0,
                ),
            ),
            trackingSession = ExplorationTrackingSession(
                lastKnownLocation = GeoPoint(lat = 50.45, lon = 30.52),
            ),
        )
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )

        try {
            fixture.viewModel.awaitContent()
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = visibleBoundsInside(visibleRange),
                ),
            )
            advanceUntilIdle()

            // When
            val actual = fixture.viewModel.awaitContent { content ->
                content.visibleObjects.any { it.id == "${RESOURCE_SPAWN_ID_PREFIX}:cell-1-slot-0" }
            }

            // Then
            actual.visibleObjects.map { it.id }.toSet() shouldBe setOf("${RESOURCE_SPAWN_ID_PREFIX}:cell-1-slot-0")
            actual.visibleObjects
                .first { it.id == "${RESOURCE_SPAWN_ID_PREFIX}:cell-1-slot-0" }
                .title shouldBe "Scrap"
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    private suspend fun MapViewModel.awaitContent(
        predicate: (MapUiState.Content) -> Boolean = { true },
    ): MapUiState.Content = uiState
        .mapNotNull { it as? MapUiState.Content }
        .first(predicate)

    private fun TestScope.tearDownMainDispatcher(viewModel: MapViewModel) {
        viewModel.viewModelScope.cancel()
        advanceTimeBy(5_000L)
        runCurrent()
        advanceUntilIdle()
    }

    private data class Fixture(
        val viewModel: MapViewModel,
    )

    private fun createFixture(
        resourceSpawns: List<ResourceSpawn> = emptyList(),
        exploredTiles: Set<MapTile> = emptySet(),
        trackingSession: ExplorationTrackingSession = ExplorationTrackingSession(),
        selectedMapStyle: MapStyle = MapStyle.defaultStyle,
        tileRuntimeConfig: ExplorationTileRuntimeConfig = ExplorationTileRuntimeConfig(),
        startTrackingResult: Output<Unit, StartExplorationTrackingSessionError> = Output.Success(Unit),
    ): Fixture {
        val observeCollectibleResourceSpawns = mockk<ObserveCollectibleResourceSpawnsUseCase>()
        val observePackedExploredTiles = mockk<ObservePackedExploredTilesUseCase>()
        val getPackedExploredTiles = mockk<GetPackedExploredTilesUseCase>()
        val getExplorationTileRuntimeConfig = mockk<GetExplorationTileRuntimeConfigUseCase>()
        val appSettingsRepository = FakeAppSettingsRepository(selectedMapStyle)
        val observeExplorationTileRuntimeConfig = mockk<ObserveExplorationTileRuntimeConfigUseCase>()
        val observeExplorationTrackingSession = mockk<ObserveExplorationTrackingSessionUseCase>()
        val startTrackingSession = mockk<StartExplorationTrackingSessionUseCase>()

        val trackingSessionFlow = MutableStateFlow(trackingSession)

        every { observeCollectibleResourceSpawns.invoke(any()) } returns MutableStateFlow(Output.Success(resourceSpawns))
        every { observePackedExploredTiles.invoke(any()) } returns MutableStateFlow(
            Output.Success(exploredTiles.toPackedLongArray()),
        )
        every { getExplorationTileRuntimeConfig.invoke() } returns Output.Success(tileRuntimeConfig)
        every { observeExplorationTileRuntimeConfig.invoke() } returns MutableStateFlow(Output.Success(tileRuntimeConfig))
        every { observeExplorationTrackingSession.invoke() } returns trackingSessionFlow.map { Output.Success(it) }
        coEvery { getPackedExploredTiles.invoke(any()) } returns Output.Success(exploredTiles.toPackedLongArray())
        coEvery { startTrackingSession.invoke() } returns startTrackingResult

        return Fixture(
            viewModel = MapViewModel(
                observeCollectibleResourceSpawns = observeCollectibleResourceSpawns,
                getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfig,
                observeSelectedMapStyle = ObserveSelectedMapStyleUseCase(appSettingsRepository),
                fogOfWarControllerFactory = { scope ->
                    FogOfWarController(
                        observeExplorationTileRuntimeConfig = observeExplorationTileRuntimeConfig,
                        observeExplorationTrackingSession = observeExplorationTrackingSession,
                        observePackedExploredTiles = observePackedExploredTiles,
                        getPackedExploredTiles = getPackedExploredTiles,
                        renderDataFactory = FowRenderDataFactory(),
                        fogOfWarCalculator = FogOfWarCalculator(),
                        scope = scope,
                    )
                },
                observeExplorationTrackingSession = observeExplorationTrackingSession,
                startExplorationTrackingSession = startTrackingSession,
                mapObjectQueryWindowPolicy = MapObjectQueryWindowPolicy(),
                mapWorldObjectPresenter = MapWorldObjectPresenter(),
            ),
        )
    }

    private class FakeAppSettingsRepository(
        private val selectedMapStyle: MapStyle,
    ) : AppSettingsRepository {

        override fun observeAvailableMapStyles() =
            flowOf(Output.Success(MapStyle.availableStyles))

        override fun observeSelectedMapStyle() =
            flowOf(Output.Success(selectedMapStyle))

        override suspend fun setSelectedMapStyle(styleId: String) =
            Output.Success(Unit)
    }

    private fun resourceSpawn(
        id: String,
        resourceTypeId: String,
        lat: Double,
        lon: Double,
        collectionRadiusMeters: Double,
    ): ResourceSpawn = ResourceSpawn(
        id = id,
        typeId = resourceTypeId,
        position = GeoPoint(lat = lat, lon = lon),
        collectionRadiusMeters = collectionRadiusMeters,
    )

    private fun visibleBoundsInside(range: ExplorationTileRange): GeoBounds =
        bounds(range).let { bounds ->
            GeoBounds(
                south = bounds.south + VIEWPORT_BOUNDS_EPSILON,
                west = bounds.west + VIEWPORT_BOUNDS_EPSILON,
                north = bounds.north - VIEWPORT_BOUNDS_EPSILON,
                east = bounds.east - VIEWPORT_BOUNDS_EPSILON,
            )
        }

    private fun Set<MapTile>.toPackedLongArray(): LongArray =
        map(MapTile::packedValue).toLongArray()

    private companion object {
        @JvmStatic
        @AfterClass
        fun resetMainDispatcherAfterClass() {
            Dispatchers.resetMain()
        }

        const val VIEWPORT_BOUNDS_EPSILON = 1e-6
        const val RESOURCE_SPAWN_ID_PREFIX = "spawn"
    }
}
