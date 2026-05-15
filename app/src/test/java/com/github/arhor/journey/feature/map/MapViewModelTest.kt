@file:Suppress("UnusedFlow")

package com.github.arhor.journey.feature.map

import androidx.lifecycle.viewModelScope
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.CANONICAL_ZOOM
import com.github.arhor.journey.domain.internal.bounds
import com.github.arhor.journey.domain.model.BreachNodeDefinition
import com.github.arhor.journey.domain.model.BreachNodeRecord
import com.github.arhor.journey.domain.model.BreachNodeState
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.error.StartExplorationTrackingSessionError
import com.github.arhor.journey.domain.repository.AppSettingsRepository
import com.github.arhor.journey.domain.usecase.CompleteBreachUseCase
import com.github.arhor.journey.domain.usecase.DiscoverBreachNodeUseCase
import com.github.arhor.journey.domain.usecase.FindNearestBreachNodeUseCase
import com.github.arhor.journey.domain.usecase.GetPackedExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveControlledBreachRevealCellsUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObservePackedExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.ObserveVisibleBreachNodesUseCase
import com.github.arhor.journey.domain.usecase.StartExplorationTrackingSessionUseCase
import com.github.arhor.journey.feature.map.fow.FogOfWarCalculator
import com.github.arhor.journey.feature.map.fow.FogOfWarController
import com.github.arhor.journey.feature.map.fow.FowRenderDataFactory
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Test

class MapViewModelTest {

    @Test
    fun `uiState should expose idle breach protocol state before pulse`() = runTest {
        // Given
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = createFixture()

        try {
            // When
            val actual = fixture.viewModel.awaitContent()

            // Then
            actual.breachProtocol shouldBe BreachProtocolUiState.Idle
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should expose signal locked breach protocol state after pulse`() = runTest {
        // Given
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actorLocation = GeoPoint(lat = 50.4500, lon = 30.5200)
        val breachRecord = breachRecord(
            id = "breach-node:v1:h3r9:cell-1",
            cellId = "cell-1",
            location = actorLocation,
        )
        val findNearestBreachNode = mockk<FindNearestBreachNodeUseCase>()
        val discoverBreachNode = mockk<DiscoverBreachNodeUseCase>()
        coEvery { findNearestBreachNode.invoke(actorLocation) } returns Output.Success(breachRecord)
        coEvery {
            discoverBreachNode.invoke(
                id = breachRecord.definition.id,
                actorLocation = actorLocation,
            )
        } returns Output.Success(
            BreachNodeState(
                breachNodeId = breachRecord.definition.id,
                h3CellId = breachRecord.definition.h3CellId,
                discoveredAt = FIXED_INSTANT,
                controlledAt = null,
                lockdownUntil = null,
                updatedAt = FIXED_INSTANT,
            ),
        )
        val fixture = createFixture(
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = actorLocation,
            ),
            findNearestBreachNode = findNearestBreachNode,
            discoverBreachNode = discoverBreachNode,
        )

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.PulseClicked)
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent { content ->
                content.breachProtocol is BreachProtocolUiState.SignalLocked
            }
            actual.breachProtocol shouldBe BreachProtocolUiState.SignalLocked(
                breachNodeId = breachRecord.definition.id,
                districtName = breachRecord.definition.districtName,
                distanceMeters = 0,
                signalStrengthPercent = 100,
                canStartUpload = true,
                disabledReason = null,
            )
            coVerify(exactly = 1) { findNearestBreachNode.invoke(actorLocation) }
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should complete breach upload after repeated ticks`() = runTest {
        // Given
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actorLocation = GeoPoint(lat = 50.4500, lon = 30.5200)
        val breachRecord = breachRecord(
            id = "breach-node:v1:h3r9:cell-2",
            cellId = "cell-2",
            location = actorLocation,
        )
        val findNearestBreachNode = mockk<FindNearestBreachNodeUseCase>()
        val discoverBreachNode = mockk<DiscoverBreachNodeUseCase>()
        val completeBreach = mockk<CompleteBreachUseCase>()
        coEvery { findNearestBreachNode.invoke(actorLocation) } returns Output.Success(breachRecord)
        coEvery {
            discoverBreachNode.invoke(
                id = breachRecord.definition.id,
                actorLocation = actorLocation,
            )
        } returns Output.Success(
            BreachNodeState(
                breachNodeId = breachRecord.definition.id,
                h3CellId = breachRecord.definition.h3CellId,
                discoveredAt = FIXED_INSTANT,
                controlledAt = null,
                lockdownUntil = null,
                updatedAt = FIXED_INSTANT,
            ),
        )
        coEvery {
            completeBreach.invoke(
                id = breachRecord.definition.id,
                actorLocation = actorLocation,
            )
        } returns Output.Success(
            BreachNodeState(
                breachNodeId = breachRecord.definition.id,
                h3CellId = breachRecord.definition.h3CellId,
                discoveredAt = FIXED_INSTANT,
                controlledAt = FIXED_INSTANT,
                lockdownUntil = null,
                updatedAt = FIXED_INSTANT,
            ),
        )
        val fixture = createFixture(
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = actorLocation,
            ),
            findNearestBreachNode = findNearestBreachNode,
            discoverBreachNode = discoverBreachNode,
            completeBreach = completeBreach,
        )

        try {
            fixture.viewModel.awaitContent()
            fixture.viewModel.dispatch(MapIntent.PulseClicked)
            advanceUntilIdle()
            fixture.viewModel.dispatch(MapIntent.StartBreachUpload)
            advanceUntilIdle()

            repeat(4) {
                fixture.viewModel.dispatch(MapIntent.BreachUploadTick)
                advanceUntilIdle()
            }

            // Then
            val actual = fixture.viewModel.awaitContent { content ->
                content.breachProtocol is BreachProtocolUiState.Completed
            }
            actual.breachProtocol shouldBe BreachProtocolUiState.Completed(
                districtName = breachRecord.definition.districtName,
            )
            coVerify(exactly = 1) {
                completeBreach.invoke(
                    id = breachRecord.definition.id,
                    actorLocation = actorLocation,
                )
            }
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

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
    fun `uiState should expose empty visible objects when viewport changes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
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
                content.visibleObjects.isEmpty()
            }

            // Then
            actual.visibleObjects shouldBe emptyList()
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
        Dispatchers.resetMain()
    }

    private fun breachRecord(
        id: String,
        cellId: String,
        location: GeoPoint,
    ): BreachNodeRecord =
        BreachNodeRecord(
            definition = BreachNodeDefinition(
                id = id,
                h3CellId = cellId,
                districtName = "district-$cellId",
                description = null,
                location = location,
                interactionRadiusMeters = 35.0,
                controlledH3CellIds = setOf(cellId),
            ),
            state = BreachNodeState(
                breachNodeId = id,
                h3CellId = cellId,
                discoveredAt = FIXED_INSTANT,
                controlledAt = null,
                lockdownUntil = null,
                updatedAt = FIXED_INSTANT,
            ),
        )

    private data class Fixture(
        val viewModel: MapViewModel,
    )

    private fun createFixture(
        exploredTiles: Set<MapTile> = emptySet(),
        trackingSession: ExplorationTrackingSession = ExplorationTrackingSession(),
        selectedMapStyle: MapStyle = MapStyle.defaultStyle,
        tileRuntimeConfig: ExplorationTileRuntimeConfig = ExplorationTileRuntimeConfig(),
        startTrackingResult: Output<Unit, StartExplorationTrackingSessionError> = Output.Success(Unit),
        findNearestBreachNode: FindNearestBreachNodeUseCase = mockk(),
        discoverBreachNode: DiscoverBreachNodeUseCase = mockk(),
        completeBreach: CompleteBreachUseCase = mockk(),
        observeVisibleBreachNodes: ObserveVisibleBreachNodesUseCase = mockk(),
        observeControlledBreachRevealCells: ObserveControlledBreachRevealCellsUseCase = mockk(),
    ): Fixture {
        val observePackedExploredTiles = mockk<ObservePackedExploredTilesUseCase>()
        val getPackedExploredTiles = mockk<GetPackedExploredTilesUseCase>()
        val appSettingsRepository = FakeAppSettingsRepository(selectedMapStyle)
        val observeExplorationTileRuntimeConfig = mockk<ObserveExplorationTileRuntimeConfigUseCase>()
        val observeExplorationTrackingSession = mockk<ObserveExplorationTrackingSessionUseCase>()
        val startTrackingSession = mockk<StartExplorationTrackingSessionUseCase>()

        val trackingSessionFlow = MutableStateFlow(trackingSession)

        every { observePackedExploredTiles.invoke(any()) } returns MutableStateFlow(
            Output.Success(exploredTiles.toPackedLongArray()),
        )
        every { observeExplorationTileRuntimeConfig.invoke() } returns MutableStateFlow(Output.Success(tileRuntimeConfig))
        every { observeExplorationTrackingSession.invoke() } returns trackingSessionFlow.map { Output.Success(it) }
        every { observeVisibleBreachNodes.invoke(any()) } returns flowOf(Output.Success(emptyList()))
        every { observeControlledBreachRevealCells.invoke(any()) } returns flowOf(Output.Success(emptySet()))
        coEvery { getPackedExploredTiles.invoke(any()) } returns Output.Success(exploredTiles.toPackedLongArray())
        coEvery { startTrackingSession.invoke() } returns startTrackingResult

        return Fixture(
            viewModel = MapViewModel(
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
                findNearestBreachNode = findNearestBreachNode,
                discoverBreachNode = discoverBreachNode,
                completeBreach = completeBreach,
                observeVisibleBreachNodes = observeVisibleBreachNodes,
                observeControlledBreachRevealCells = observeControlledBreachRevealCells,
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
        const val VIEWPORT_BOUNDS_EPSILON = 1e-6
        val FIXED_INSTANT = java.time.Instant.parse("2026-05-15T12:00:00Z")
    }
}
