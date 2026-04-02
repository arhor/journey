package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.CANONICAL_ZOOM
import com.github.arhor.journey.domain.internal.bounds
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.WatchtowerRevealSnapshot
import com.github.arhor.journey.domain.usecase.GetExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveClaimedWatchtowerRevealTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObserveExploredTilesUseCase
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

    private fun createFixture(
        scope: CoroutineScope,
        exploredTiles: Set<MapTile>,
        trackingSession: ExplorationTrackingSession = ExplorationTrackingSession(),
        watchtowerRevealSnapshot: WatchtowerRevealSnapshot = WatchtowerRevealSnapshot(emptySet()),
    ): Fixture {
        val observeExplorationTileRuntimeConfig = mockk<ObserveExplorationTileRuntimeConfigUseCase>()
        val observeExplorationTrackingSession = mockk<ObserveExplorationTrackingSessionUseCase>()
        val observeExploredTiles = mockk<ObserveExploredTilesUseCase>()
        val observeClaimedWatchtowerRevealTiles = mockk<ObserveClaimedWatchtowerRevealTilesUseCase>()
        val getExploredTiles = mockk<GetExploredTilesUseCase>()
        val configFlow = MutableStateFlow(Output.Success(ExplorationTileRuntimeConfig()))
        val trackingSessionFlow = MutableStateFlow(Output.Success(trackingSession))
        val exploredTilesFlow = MutableStateFlow(Output.Success(exploredTiles))
        val watchtowerRevealFlow = MutableStateFlow(Output.Success(watchtowerRevealSnapshot))

        every { observeExplorationTileRuntimeConfig.invoke() } returns configFlow
        every { observeExplorationTrackingSession.invoke() } returns trackingSessionFlow
        every { observeExploredTiles.invoke(any()) } returns exploredTilesFlow
        every { observeClaimedWatchtowerRevealTiles.invoke(any(), any()) } returns watchtowerRevealFlow
        coEvery { getExploredTiles.invoke(any()) } returns Output.Success(exploredTiles)

        return Fixture(
            controller = FogOfWarController(
                observeExplorationTileRuntimeConfig = observeExplorationTileRuntimeConfig,
                observeExplorationTrackingSession = observeExplorationTrackingSession,
                observeExploredTiles = observeExploredTiles,
                observeClaimedWatchtowerRevealTiles = observeClaimedWatchtowerRevealTiles,
                getExploredTiles = getExploredTiles,
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

    private data class Fixture(
        val controller: FogOfWarController,
    )

    private companion object {
        const val VIEWPORT_BOUNDS_EPSILON = 1e-7
    }
}
