package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.CANONICAL_ZOOM
import com.github.arhor.journey.domain.internal.bounds
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.WatchtowerRevealSnapshot
import com.github.arhor.journey.domain.usecase.GetPackedExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveClaimedWatchtowerRevealTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObservePackedExploredTilesUseCase
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FogOfWarControllerTest {

    @Test
    fun `uiState should dim explored tiles when there is no usable current location`() = runTest {
        // Given
        val controllerScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val exploredTile = MapTile(
            zoom = visibleRange.zoom,
            x = visibleRange.minX,
            y = visibleRange.minY,
        )
        val fixture = createFixture(scope = controllerScope, exploredTiles = setOf(exploredTile))

        try {
            // When
            fixture.controller.updateViewport(visibleBoundsInside(visibleRange))
            advanceUntilIdle()

            // Then
            val actual = fixture.controller.uiState.first { it.hiddenExploredRenderData != null }
            actual.visibleExploredTileCount shouldBe 0
            actual.hiddenExploredRenderData.shouldNotBeNull()
        } finally {
            controllerScope.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun `uiState should treat claimed watchtower coverage as cleared and visible fog tiles`() = runTest {
        // Given
        val controllerScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val revealedTile = MapTile(
            zoom = visibleRange.zoom,
            x = visibleRange.minX,
            y = visibleRange.minY,
        )
        val fixture = createFixture(
            scope = controllerScope,
            exploredTiles = emptySet(),
            watchtowerRevealSnapshot = WatchtowerRevealSnapshot(
                tiles = setOf(revealedTile),
                revision = 7,
            ),
        )

        try {
            // When
            fixture.controller.updateViewport(visibleBoundsInside(visibleRange))
            advanceUntilIdle()

            // Then
            val actual = fixture.controller.uiState.first { it.visibleExploredTileCount == 1 }
            actual.visibleExploredTileCount shouldBe 1
            actual.hiddenExploredRenderData shouldBe null
            fixture.controller.visibilityState.first().visibilityTileMask shouldBe setOf(revealedTile)
        } finally {
            controllerScope.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun `uiState should union walked exploration with claimed watchtower coverage when deriving hidden explored fog`() = runTest {
        // Given
        val controllerScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val walkedTile = MapTile(
            zoom = visibleRange.zoom,
            x = visibleRange.maxX,
            y = visibleRange.maxY,
        )
        val revealedTile = MapTile(
            zoom = visibleRange.zoom,
            x = visibleRange.minX,
            y = visibleRange.minY,
        )
        val fixture = createFixture(
            scope = controllerScope,
            exploredTiles = setOf(walkedTile),
            watchtowerRevealSnapshot = WatchtowerRevealSnapshot(
                tiles = setOf(revealedTile),
                revision = 3,
            ),
        )

        try {
            // When
            fixture.controller.updateViewport(visibleBoundsInside(visibleRange))
            advanceUntilIdle()

            // Then
            val actual = fixture.controller.uiState.first { it.hiddenExploredRenderData != null }
            actual.visibleExploredTileCount shouldBe 1
            actual.hiddenExploredRenderData.shouldNotBeNull()
            fixture.controller.visibilityState.first().visibilityTileMask shouldBe setOf(revealedTile)
        } finally {
            controllerScope.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun `uiState should keep visible explored tiles out of hidden explored render data when tracking location reveals them`() = runTest {
        // Given
        val controllerScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 10,
            minY = 20,
            maxY = 20,
        )
        val exploredTile = MapTile(
            zoom = visibleRange.zoom,
            x = visibleRange.minX,
            y = visibleRange.minY,
        )
        val fixture = createFixture(
            scope = controllerScope,
            exploredTiles = setOf(exploredTile),
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = centerPointOf(exploredTile),
            ),
        )

        try {
            // When
            fixture.controller.updateViewport(visibleBoundsInside(visibleRange))
            advanceUntilIdle()

            // Then
            val actual = fixture.controller.uiState.first { it.visibleExploredTileCount == 1 }
            actual.visibleExploredTileCount shouldBe 1
            actual.hiddenExploredRenderData shouldBe null
            fixture.controller.visibilityState.first().visibilityTileMask.contains(exploredTile) shouldBe true
        } finally {
            controllerScope.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun `uiState should derive hidden explored render data from unsorted duplicate packed explored tiles`() = runTest {
        // Given
        val controllerScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 500_000,
            maxX = 500_012,
            minY = 500_000,
            maxY = 500_000,
        )
        val visibleTile = MapTile(
            zoom = visibleRange.zoom,
            x = visibleRange.minX,
            y = visibleRange.minY,
        )
        val hiddenTile = MapTile(
            zoom = visibleRange.zoom,
            x = visibleRange.maxX,
            y = visibleRange.minY,
        )
        val fixture = createFixture(
            scope = controllerScope,
            exploredTiles = setOf(visibleTile, hiddenTile),
            packedExploredTiles = longArrayOf(
                hiddenTile.packedValue,
                visibleTile.packedValue,
                hiddenTile.packedValue,
            ),
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = centerPointOf(visibleTile),
            ),
        )

        try {
            // When
            fixture.controller.updateViewport(visibleBoundsInside(visibleRange))
            advanceUntilIdle()

            // Then
            val actual = fixture.controller.uiState.first {
                it.visibleExploredTileCount == 1 && it.hiddenExploredRenderData != null
            }
            actual.visibleExploredTileCount shouldBe 1
            actual.hiddenExploredRenderData.shouldNotBeNull()
            fixture.controller.visibilityState.first().visibilityTileMask.contains(visibleTile) shouldBe true
        } finally {
            controllerScope.cancel()
            advanceUntilIdle()
        }
    }

    private fun createFixture(
        scope: CoroutineScope,
        exploredTiles: Set<MapTile>,
        packedExploredTiles: LongArray = exploredTiles.toPackedLongArray(),
        trackingSession: ExplorationTrackingSession = ExplorationTrackingSession(),
        watchtowerRevealSnapshot: WatchtowerRevealSnapshot = WatchtowerRevealSnapshot(emptySet()),
    ): Fixture {
        val observeExplorationTileRuntimeConfig = mockk<ObserveExplorationTileRuntimeConfigUseCase>()
        val observeExplorationTrackingSession = mockk<ObserveExplorationTrackingSessionUseCase>()
        val observePackedExploredTiles = mockk<ObservePackedExploredTilesUseCase>()
        val observeClaimedWatchtowerRevealTiles = mockk<ObserveClaimedWatchtowerRevealTilesUseCase>()
        val getPackedExploredTiles = mockk<GetPackedExploredTilesUseCase>()
        val configFlow = MutableStateFlow(Output.Success(ExplorationTileRuntimeConfig()))
        val trackingSessionFlow = MutableStateFlow(Output.Success(trackingSession))
        val exploredTilesFlow = MutableStateFlow(Output.Success(packedExploredTiles))
        val watchtowerRevealFlow = MutableStateFlow(Output.Success(watchtowerRevealSnapshot))

        every { observeExplorationTileRuntimeConfig.invoke() } returns configFlow
        every { observeExplorationTrackingSession.invoke() } returns trackingSessionFlow
        every { observePackedExploredTiles.invoke(any()) } returns exploredTilesFlow
        every { observeClaimedWatchtowerRevealTiles.invoke(any(), any()) } returns watchtowerRevealFlow
        coEvery { getPackedExploredTiles.invoke(any()) } returns Output.Success(packedExploredTiles)

        return Fixture(
            controller = FogOfWarController(
                observeExplorationTileRuntimeConfig = observeExplorationTileRuntimeConfig,
                observeExplorationTrackingSession = observeExplorationTrackingSession,
                observePackedExploredTiles = observePackedExploredTiles,
                observeClaimedWatchtowerRevealTiles = observeClaimedWatchtowerRevealTiles,
                getPackedExploredTiles = getPackedExploredTiles,
                renderDataFactory = FowRenderDataFactory(),
                fogOfWarCalculator = FogOfWarCalculator(),
                scope = scope,
            ),
        )
    }

    private fun visibleBoundsInside(range: ExplorationTileRange): GeoBounds =
        bounds(range).let { rangeBounds ->
            GeoBounds(
                south = rangeBounds.south + VIEWPORT_BOUNDS_EPSILON,
                west = rangeBounds.west + VIEWPORT_BOUNDS_EPSILON,
                north = rangeBounds.north - VIEWPORT_BOUNDS_EPSILON,
                east = rangeBounds.east - VIEWPORT_BOUNDS_EPSILON,
            )
        }

    private fun centerPointOf(tile: MapTile): GeoPoint =
        bounds(tile).let { tileBounds ->
            GeoPoint(
                lat = (tileBounds.south + tileBounds.north) / 2.0,
                lon = (tileBounds.west + tileBounds.east) / 2.0,
            )
        }

    private fun Set<MapTile>.toPackedLongArray(): LongArray =
        map(MapTile::packedValue).toLongArray()

    private data class Fixture(
        val controller: FogOfWarController,
    )

    private companion object {
        const val VIEWPORT_BOUNDS_EPSILON = 1e-7
    }
}
