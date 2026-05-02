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
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.usecase.GetPackedExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveClaimedWatchtowerRevealTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObservePackedExploredTilesUseCase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

private typealias PackedExploredTilesFlow = MutableStateFlow<Output<LongArray, UseCaseError>>
private typealias WatchtowerRevealFlow = MutableStateFlow<Output<WatchtowerRevealSnapshot, UseCaseError>>

class FogOfWarControllerTest {

    @Test
    fun `updateViewport should activate initial displayed buffer when fog is not suppressed`() = runTest {
        // Given
        val controllerScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val expectedBuffer = createFogBufferRegion(visibleRange)
        val fixture = createFixture(scope = controllerScope, exploredTiles = emptySet())

        try {
            // When
            fixture.controller.updateViewport(visibleBoundsInside(visibleRange))
            advanceUntilIdle()

            // Then
            val actual = fixture.controller.uiState.first {
                it.visibleTileRange == visibleRange && it.activeRenderData != null
            }
            actual.triggerBounds shouldBe expectedBuffer.triggerBounds
            actual.bufferedBounds shouldBe expectedBuffer.bufferedBounds
            actual.fogRanges shouldBe listOf(expectedBuffer.bufferedTileRange)
            actual.isSuppressed shouldBe false
            actual.isRecomputing shouldBe false
            actual.handoffRenderData shouldBe null
        } finally {
            controllerScope.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun `updateViewport should expose handoff render data while pending buffer recomputes`() = runTest {
        // Given
        val controllerScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val initialVisibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val pendingVisibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 18,
            maxX = 19,
            minY = 20,
            maxY = 21,
        )
        val pendingBufferRange = expectedFogBufferRange(pendingVisibleRange)
        val allowPendingSwap = CompletableDeferred<Unit>()
        val fixture = createFixture(
            scope = controllerScope,
            exploredTiles = emptySet(),
            getPackedExploredTiles = { range ->
                if (range == pendingBufferRange) {
                    allowPendingSwap.await()
                }
                Output.Success(LongArray(0))
            },
        )

        try {
            fixture.controller.updateViewport(visibleBoundsInside(initialVisibleRange))
            advanceUntilIdle()
            fixture.controller.uiState.first {
                it.visibleTileRange == initialVisibleRange && it.activeRenderData != null
            }

            // When
            fixture.controller.updateViewport(visibleBoundsInside(pendingVisibleRange))
            runCurrent()

            // Then
            val recomputing = fixture.controller.uiState.first {
                it.visibleTileRange == pendingVisibleRange && it.isRecomputing
            }
            recomputing.activeRenderData.shouldNotBeNull()
            recomputing.handoffRenderData.shouldNotBeNull()

            allowPendingSwap.complete(Unit)
            advanceUntilIdle()

            val swapped = fixture.controller.uiState.first {
                it.visibleTileRange == pendingVisibleRange && !it.isRecomputing
            }
            swapped.handoffRenderData shouldBe null
            swapped.activeRenderData.shouldNotBeNull()
        } finally {
            allowPendingSwap.complete(Unit)
            controllerScope.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun `updateViewport should suppress and clear fog buffers when visible tile count exceeds the safety limit`() =
        runTest {
            // Given
            val controllerScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
            val visibleRange = ExplorationTileRange(
                zoom = CANONICAL_ZOOM,
                minX = 10,
                maxX = 8_202,
                minY = 20,
                maxY = 20,
            )
            val fixture = createFixture(scope = controllerScope, exploredTiles = emptySet())

            try {
                // When
                fixture.controller.updateViewport(visibleBoundsInside(visibleRange))
                advanceUntilIdle()

                // Then
                val actual = fixture.controller.uiState.first { it.visibleTileRange == visibleRange }
                actual.isSuppressed shouldBe true
                actual.isRecomputing shouldBe false
                actual.activeRenderData shouldBe null
                actual.handoffRenderData shouldBe null
                actual.bufferedBounds shouldBe null
                actual.fogRanges.shouldBeEmpty()
            } finally {
                controllerScope.cancel()
                advanceUntilIdle()
            }
        }

    @Test
    fun `updateViewport should recover after buffered tile count suppression clears`() = runTest {
        // Given
        val controllerScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val bufferedLimitRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 8_201,
            minY = 20,
            maxY = 20,
        )
        val fixture = createFixture(scope = controllerScope, exploredTiles = emptySet())

        try {
            fixture.controller.updateViewport(visibleBoundsInside(visibleRange))
            advanceUntilIdle()
            fixture.controller.uiState.first {
                it.visibleTileRange == visibleRange && it.activeRenderData != null
            }

            // When
            fixture.controller.updateViewport(visibleBoundsInside(bufferedLimitRange))
            advanceUntilIdle()

            // Then
            val suppressed = fixture.controller.uiState.first { it.visibleTileRange == bufferedLimitRange }
            suppressed.isSuppressed shouldBe true
            suppressed.activeRenderData shouldBe null
            suppressed.bufferedBounds shouldBe null

            fixture.controller.updateViewport(visibleBoundsInside(visibleRange))
            advanceUntilIdle()

            val recovered = fixture.controller.uiState.first {
                it.visibleTileRange == visibleRange && it.activeRenderData != null
            }
            recovered.isSuppressed shouldBe false
            recovered.isRecomputing shouldBe false
        } finally {
            controllerScope.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun `updateViewport should recover from pending explored tile failure when a newer buffer is requested`() =
        runTest {
            // Given
            val controllerScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
            val initialVisibleRange = ExplorationTileRange(
                zoom = CANONICAL_ZOOM,
                minX = 10,
                maxX = 11,
                minY = 20,
                maxY = 21,
            )
            val failedPendingVisibleRange = ExplorationTileRange(
                zoom = CANONICAL_ZOOM,
                minX = 18,
                maxX = 19,
                minY = 20,
                maxY = 21,
            )
            val recoveryVisibleRange = ExplorationTileRange(
                zoom = CANONICAL_ZOOM,
                minX = 26,
                maxX = 27,
                minY = 20,
                maxY = 21,
            )
            val failedPendingBufferRange = expectedFogBufferRange(failedPendingVisibleRange)
            var shouldFailPendingRequest = true
            val fixture = createFixture(
                scope = controllerScope,
                exploredTiles = emptySet(),
                getPackedExploredTiles = { range ->
                    if (range == failedPendingBufferRange && shouldFailPendingRequest) {
                        shouldFailPendingRequest = false
                        Output.Failure(UseCaseError.Unexpected("load pending fog tiles", RuntimeException("boom")))
                    } else {
                        Output.Success(LongArray(0))
                    }
                },
            )

            try {
                fixture.controller.updateViewport(visibleBoundsInside(initialVisibleRange))
                advanceUntilIdle()
                fixture.controller.uiState.first {
                    it.visibleTileRange == initialVisibleRange && it.activeRenderData != null
                }

                fixture.controller.updateViewport(visibleBoundsInside(failedPendingVisibleRange))
                advanceUntilIdle()
                fixture.controller.uiState.first {
                    it.visibleTileRange == failedPendingVisibleRange && it.isRecomputing
                }

                // When
                fixture.controller.updateViewport(visibleBoundsInside(recoveryVisibleRange))
                advanceUntilIdle()

                // Then
                val recovered = fixture.controller.uiState.first {
                    it.visibleTileRange == recoveryVisibleRange && !it.isRecomputing
                }
                recovered.activeRenderData.shouldNotBeNull()
                recovered.handoffRenderData shouldBe null
            } finally {
                controllerScope.cancel()
                advanceUntilIdle()
            }
        }

    @Test
    fun `visibility refresh should update hidden explored render data when live visibility changes`() = runTest {
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
        )

        try {
            fixture.controller.updateViewport(visibleBoundsInside(visibleRange))
            advanceUntilIdle()
            fixture.controller.uiState.first {
                it.visibleExploredTileCount == 0 && it.hiddenExploredRenderData != null
            }

            // When
            fixture.trackingSessionFlow.value = Output.Success(
                ExplorationTrackingSession(
                    isActive = true,
                    status = ExplorationTrackingStatus.TRACKING,
                    lastKnownLocation = centerPointOf(exploredTile),
                ),
            )
            advanceUntilIdle()

            // Then
            val actual = fixture.controller.uiState.first {
                it.visibleExploredTileCount == 1 && it.hiddenExploredRenderData == null
            }
            actual.hiddenExploredRenderData shouldBe null
            fixture.controller.visibilityState.first().visibilityTileMask.contains(exploredTile) shouldBe true
        } finally {
            controllerScope.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun `runtime config should replace displayed buffer when canonical zoom changes`() = runTest {
        // Given
        val controllerScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val initialVisibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val visibleBounds = visibleBoundsInside(initialVisibleRange)
        val updatedZoom = CANONICAL_ZOOM + 1
        val updatedVisibleRange = createFogViewportSnapshot(
            visibleBounds = visibleBounds,
            canonicalZoom = updatedZoom,
        ).visibleTileRange
        val expectedBuffer = createFogBufferRegion(updatedVisibleRange)
        val fixture = createFixture(scope = controllerScope, exploredTiles = emptySet())

        try {
            fixture.controller.updateViewport(visibleBounds)
            advanceUntilIdle()
            fixture.controller.uiState.first {
                it.canonicalZoom == CANONICAL_ZOOM && it.activeRenderData != null
            }

            // When
            fixture.configFlow.value = Output.Success(
                ExplorationTileRuntimeConfig(canonicalZoom = updatedZoom),
            )
            advanceUntilIdle()

            // Then
            val actual = fixture.controller.uiState.first {
                it.canonicalZoom == updatedZoom && it.activeRenderData != null
            }
            actual.visibleBounds shouldBe visibleBounds
            actual.visibleTileRange shouldBe updatedVisibleRange
            actual.triggerBounds shouldBe expectedBuffer.triggerBounds
            actual.bufferedBounds shouldBe expectedBuffer.bufferedBounds
            actual.isRecomputing shouldBe false
        } finally {
            controllerScope.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun `updateViewport should keep fog stable when viewport remains inside trigger bounds`() =
        runTest {
            // Given
            val controllerScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
            val initialVisibleRange = ExplorationTileRange(
                zoom = CANONICAL_ZOOM,
                minX = 10,
                maxX = 11,
                minY = 20,
                maxY = 21,
            )
            val shiftedVisibleRange = ExplorationTileRange(
                zoom = CANONICAL_ZOOM,
                minX = 11,
                maxX = 12,
                minY = 20,
                maxY = 21,
            )
            val expectedBuffer = createFogBufferRegion(initialVisibleRange)
            val fixture = createFixture(scope = controllerScope, exploredTiles = emptySet())

            try {
                fixture.controller.updateViewport(visibleBoundsInside(initialVisibleRange))
                advanceUntilIdle()
                fixture.controller.uiState.first {
                    it.visibleTileRange == initialVisibleRange && it.activeRenderData != null
                }

                // When
                fixture.controller.updateViewport(visibleBoundsInside(shiftedVisibleRange))
                runCurrent()

                // Then
                val actual = fixture.controller.uiState.first {
                    it.visibleTileRange == shiftedVisibleRange
                }
                actual.bufferedBounds shouldBe expectedBuffer.bufferedBounds
                actual.activeRenderData.shouldNotBeNull()
                actual.handoffRenderData shouldBe null
                actual.isRecomputing shouldBe false
                verify(exactly = 1) { fixture.observePackedExploredTiles.invoke(any()) }
            } finally {
                controllerScope.cancel()
                advanceUntilIdle()
            }
        }

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
    fun `uiState should combine walked and watchtower coverage for hidden explored fog`() = runTest {
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
    fun `uiState should exclude visible explored tiles from hidden explored render data`() = runTest {
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
        runtimeConfig: ExplorationTileRuntimeConfig = ExplorationTileRuntimeConfig(),
        trackingSession: ExplorationTrackingSession = ExplorationTrackingSession(),
        watchtowerRevealSnapshot: WatchtowerRevealSnapshot = WatchtowerRevealSnapshot(emptySet()),
        observePackedExploredTilesFlowFactory: (ExplorationTileRange) -> PackedExploredTilesFlow =
            { MutableStateFlow(Output.Success(packedExploredTiles)) },
        observeWatchtowerRevealFlowFactory: (GeoBounds, Int) -> WatchtowerRevealFlow =
            { _, _ -> MutableStateFlow(Output.Success(watchtowerRevealSnapshot)) },
        getPackedExploredTiles: suspend (ExplorationTileRange) -> Output<LongArray, UseCaseError> =
            { Output.Success(packedExploredTiles) },
    ): Fixture {
        val observeExplorationTileRuntimeConfig = mockk<ObserveExplorationTileRuntimeConfigUseCase>()
        val observeExplorationTrackingSession = mockk<ObserveExplorationTrackingSessionUseCase>()
        val observePackedExploredTiles = mockk<ObservePackedExploredTilesUseCase>()
        val observeClaimedWatchtowerRevealTiles = mockk<ObserveClaimedWatchtowerRevealTilesUseCase>()
        val getPackedExploredTilesUseCase = mockk<GetPackedExploredTilesUseCase>()
        val configFlow =
            MutableStateFlow<Output<ExplorationTileRuntimeConfig, UseCaseError>>(Output.Success(runtimeConfig))
        val trackingSessionFlow =
            MutableStateFlow<Output<ExplorationTrackingSession, UseCaseError>>(Output.Success(trackingSession))

        every { observeExplorationTileRuntimeConfig.invoke() } returns configFlow
        every { observeExplorationTrackingSession.invoke() } returns trackingSessionFlow
        every { observePackedExploredTiles.invoke(any()) } answers {
            observePackedExploredTilesFlowFactory(firstArg())
        }
        every { observeClaimedWatchtowerRevealTiles.invoke(any(), any()) } answers {
            observeWatchtowerRevealFlowFactory(firstArg(), secondArg())
        }
        coEvery { getPackedExploredTilesUseCase.invoke(any()) } coAnswers {
            getPackedExploredTiles(firstArg())
        }

        return Fixture(
            controller = FogOfWarController(
                observeExplorationTileRuntimeConfig = observeExplorationTileRuntimeConfig,
                observeExplorationTrackingSession = observeExplorationTrackingSession,
                observePackedExploredTiles = observePackedExploredTiles,
                observeClaimedWatchtowerRevealTiles = observeClaimedWatchtowerRevealTiles,
                getPackedExploredTiles = getPackedExploredTilesUseCase,
                renderDataFactory = FowRenderDataFactory(),
                fogOfWarCalculator = FogOfWarCalculator(),
                scope = scope,
            ),
            configFlow = configFlow,
            trackingSessionFlow = trackingSessionFlow,
            observePackedExploredTiles = observePackedExploredTiles,
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

    private fun expectedFogBufferRange(visibleRange: ExplorationTileRange): ExplorationTileRange =
        createFogBufferRegion(visibleRange).bufferedTileRange

    private data class Fixture(
        val controller: FogOfWarController,
        val configFlow: MutableStateFlow<Output<ExplorationTileRuntimeConfig, UseCaseError>>,
        val trackingSessionFlow: MutableStateFlow<Output<ExplorationTrackingSession, UseCaseError>>,
        val observePackedExploredTiles: ObservePackedExploredTilesUseCase,
    )

    private companion object {
        const val VIEWPORT_BOUNDS_EPSILON = 1e-7
    }
}
