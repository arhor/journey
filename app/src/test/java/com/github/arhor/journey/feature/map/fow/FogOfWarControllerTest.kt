package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.testing.MainDispatcherRule
import com.github.arhor.journey.domain.CANONICAL_ZOOM
import com.github.arhor.journey.domain.internal.bounds
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.usecase.GetPackedExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObservePackedExploredTilesUseCase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Rule
import org.junit.Test

private typealias PackedExploredTilesFlow = MutableStateFlow<Output<LongArray, UseCaseError>>

class FogOfWarControllerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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

    private fun createFixture(
        scope: CoroutineScope,
        exploredTiles: Set<MapTile>,
        packedExploredTiles: LongArray = exploredTiles.toPackedLongArray(),
        runtimeConfig: ExplorationTileRuntimeConfig = ExplorationTileRuntimeConfig(),
        trackingSession: ExplorationTrackingSession = ExplorationTrackingSession(),
        observePackedExploredTilesFlowFactory: (ExplorationTileRange) -> PackedExploredTilesFlow =
            { MutableStateFlow(Output.Success(packedExploredTiles)) },
        getPackedExploredTiles: suspend (ExplorationTileRange) -> Output<LongArray, UseCaseError> =
            { Output.Success(packedExploredTiles) },
    ): Fixture {
        val observeExplorationTileRuntimeConfig = mockk<ObserveExplorationTileRuntimeConfigUseCase>()
        val observeExplorationTrackingSession = mockk<ObserveExplorationTrackingSessionUseCase>()
        val observePackedExploredTiles = mockk<ObservePackedExploredTilesUseCase>()
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
        coEvery { getPackedExploredTilesUseCase.invoke(any()) } coAnswers {
            getPackedExploredTiles(firstArg())
        }

        return Fixture(
            controller = FogOfWarController(
                observeExplorationTileRuntimeConfig = observeExplorationTileRuntimeConfig,
                observeExplorationTrackingSession = observeExplorationTrackingSession,
                observePackedExploredTiles = observePackedExploredTiles,
                getPackedExploredTiles = getPackedExploredTilesUseCase,
                renderDataFactory = FowRenderDataFactory(),
                fogOfWarCalculator = FogOfWarCalculator(),
                scope = scope,
            ),
            configFlow = configFlow,
            trackingSessionFlow = trackingSessionFlow,
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

    private fun Set<MapTile>.toPackedLongArray(): LongArray =
        map(MapTile::packedValue).toLongArray()

    private fun expectedFogBufferRange(visibleRange: ExplorationTileRange): ExplorationTileRange =
        createFogBufferRegion(visibleRange).bufferedTileRange

    private data class Fixture(
        val controller: FogOfWarController,
        val configFlow: MutableStateFlow<Output<ExplorationTileRuntimeConfig, UseCaseError>>,
        val trackingSessionFlow: MutableStateFlow<Output<ExplorationTrackingSession, UseCaseError>>,
    )

    private companion object {
        const val VIEWPORT_BOUNDS_EPSILON = 1e-7
    }
}
