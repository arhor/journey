package com.github.arhor.journey.feature.map

import com.github.arhor.journey.core.common.DomainError
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.DiscoveredPoi
import com.github.arhor.journey.domain.model.ExplorationProgress
import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileGrid
import com.github.arhor.journey.domain.model.ExplorationTilePrototype
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.PoiCategory
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.StartExplorationTrackingSessionResult
import com.github.arhor.journey.domain.usecase.ClearExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.usecase.GetExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.GetExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.ObserveCollectibleResourceSpawnsUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObserveExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.SetExplorationTileCanonicalZoomUseCase
import com.github.arhor.journey.domain.usecase.SetExplorationTileRevealRadiusUseCase
import com.github.arhor.journey.domain.usecase.StartExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.StopExplorationTrackingSessionUseCase
import com.github.arhor.journey.feature.map.fow.FogOfWarCalculator
import com.github.arhor.journey.feature.map.fow.FogOfWarController
import com.github.arhor.journey.feature.map.fow.FowRenderDataFactory
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapViewportSize
import com.github.arhor.journey.feature.map.prewarm.MapTilePrewarmer
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Ignore
import org.junit.Test
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.sqrt

class MapViewModelTest {

    @Test
    fun `uiState should map discovery flags when exploration progress contains discovered points of interest`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))

            // Given
            val pointsOfInterest = listOf(
                pointOfInterest(id = FIRST_POI_ID, lat = 49.0, lon = 24.0),
                pointOfInterest(id = SECOND_POI_ID, lat = 49.1, lon = 24.1),
            )
            val fixture = createFixture(
                pointsOfInterest = pointsOfInterest,
                explorationProgress = ExplorationProgress(
                    discovered = setOf(
                        DiscoveredPoi(
                            poiId = FIRST_POI_ID,
                            discoveredAt = Instant.parse("2026-03-01T12:00:00Z"),
                        ),
                    ),
                ),
            )

            try {
                // When
                val actual = fixture.viewModel.awaitContent()

                // Then
                actual.selectedStyle shouldBe fixture.mapStyle
                actual.visibleObjects.map { it.id to it.isDiscovered }.toMap() shouldBe mapOf(
                    "${POI_ID_PREFIX}:$FIRST_POI_ID" to true,
                    "${POI_ID_PREFIX}:$SECOND_POI_ID" to false,
                )
            } finally {
                tearDownMainDispatcher()
            }
        }

    @Test
    fun `uiState should include resource spawns alongside points of interest`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            pointsOfInterest = listOf(
                pointOfInterest(id = FIRST_POI_ID, lat = 50.45, lon = 30.52),
            ),
            resourceSpawns = listOf(
                resourceSpawn(
                    id = "cell-1-slot-0",
                    resourceTypeId = "wood",
                    lat = 50.46,
                    lon = 30.53,
                    collectionRadiusMeters = 24.0,
                ),
            ),
        )
        val visibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
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
            actual.visibleObjects.map { it.id }.toSet() shouldBe setOf(
                "${POI_ID_PREFIX}:$FIRST_POI_ID",
                "${RESOURCE_SPAWN_ID_PREFIX}:cell-1-slot-0",
            )
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `uiState should reuse visible objects list when camera updates do not change objects`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            pointsOfInterest = listOf(
                pointOfInterest(id = FIRST_POI_ID, lat = 50.45, lon = 30.52),
            ),
            resourceSpawns = listOf(
                resourceSpawn(
                    id = "cell-1-slot-0",
                    resourceTypeId = "wood",
                    lat = 50.46,
                    lon = 30.53,
                    collectionRadiusMeters = 24.0,
                ),
            ),
        )
        val visibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val initialVisibleBounds = visibleBoundsInside(visibleRange)
        val updatedVisibleBounds = GeoBounds(
            south = initialVisibleBounds.south + VIEWPORT_BOUNDS_EPSILON,
            west = initialVisibleBounds.west + VIEWPORT_BOUNDS_EPSILON,
            north = initialVisibleBounds.north - VIEWPORT_BOUNDS_EPSILON,
            east = initialVisibleBounds.east - VIEWPORT_BOUNDS_EPSILON,
        )

        try {
            fixture.viewModel.awaitContent()
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = initialVisibleBounds,
                ),
            )
            advanceUntilIdle()

            val initial = fixture.viewModel.awaitContent { content ->
                content.visibleObjects.any { it.id == "${RESOURCE_SPAWN_ID_PREFIX}:cell-1-slot-0" }
            }
            val initialVisibleObjects = initial.visibleObjects
            val cameraPosition = initial.cameraPosition.shouldNotBeNull()

            // When
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = updatedVisibleBounds,
                ),
            )
            fixture.viewModel.dispatch(
                MapIntent.CameraSettled(
                    position = cameraPosition,
                    origin = CameraUpdateOrigin.USER,
                ),
            )
            advanceUntilIdle()

            val actual = fixture.viewModel.awaitContent { content ->
                content.fogOfWar.visibleBounds == updatedVisibleBounds &&
                    content.cameraUpdateOrigin == CameraUpdateOrigin.USER
            }

            // Then
            actual.visibleObjects shouldBe initialVisibleObjects
            (actual.visibleObjects === initialVisibleObjects) shouldBe true
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `uiState should expose clean debug defaults by default`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture()

        try {
            // When
            val actual = fixture.viewModel.awaitContent()

            // Then
            actual.debug.isSheetVisible shouldBe false
            actual.debug.enabledInfoItems shouldBe emptySet()
            actual.fogOfWar.isOverlayEnabled shouldBe true
            actual.debug.isTilesGridOverlayEnabled shouldBe false
            actual.debug.canonicalZoom shouldBe ExplorationTilePrototype.CANONICAL_ZOOM
            actual.debug.revealRadiusMeters shouldBe ExplorationTilePrototype.REVEAL_RADIUS_METERS.toInt()
            actual.debug.renderMode shouldBe MapRenderMode.Standard
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `uiState should emit failure when selected map style output fails with message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            mapStyleOutput = Output.Failure(
                error = TestDomainError(message = "Map styles unavailable."),
            ),
        )

        try {
            // When
            val actual = fixture.viewModel.awaitFailure()

            // Then
            actual shouldBe MapUiState.Failure(errorMessage = "Map styles unavailable.")
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should toggle debug sheet visibility without affecting camera state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture()

        try {
            val initial = fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.DebugControlsClicked)
            advanceUntilIdle()
            val opened = fixture.viewModel.awaitContent { it.debug.isSheetVisible }

            fixture.viewModel.dispatch(MapIntent.DebugControlsDismissed)
            advanceUntilIdle()
            val closed = fixture.viewModel.awaitContent { !it.debug.isSheetVisible }

            // Then
            opened.cameraPosition shouldBe initial.cameraPosition
            closed.cameraPosition shouldBe initial.cameraPosition
            closed.debug.enabledInfoItems shouldBe emptySet()
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should keep debug info items independent when one item is enabled`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture()

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(
                MapIntent.DebugInfoVisibilityChanged(
                    item = MapDebugInfoItem.VisibleTiles,
                    isVisible = true,
                ),
            )
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent {
                MapDebugInfoItem.VisibleTiles in it.debug.enabledInfoItems
            }
            actual.debug.enabledInfoItems shouldBe setOf(MapDebugInfoItem.VisibleTiles)
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should update local rendering toggles without invoking actions`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture()

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.FogOfWarOverlayToggled(isEnabled = false))
            fixture.viewModel.dispatch(MapIntent.TilesGridOverlayToggled(isEnabled = true))
            fixture.viewModel.dispatch(MapIntent.MapRenderModeSelected(mode = MapRenderMode.Debug))
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent {
                !it.fogOfWar.isOverlayEnabled &&
                    it.debug.isTilesGridOverlayEnabled &&
                    it.debug.renderMode == MapRenderMode.Debug
            }
            actual.fogOfWar.isOverlayEnabled shouldBe false
            actual.debug.isTilesGridOverlayEnabled shouldBe true
            actual.debug.renderMode shouldBe MapRenderMode.Debug
            coVerify(exactly = 0) { fixture.clearExploredTiles.invoke() }
            coVerify(exactly = 0) { fixture.startTrackingSession.invoke() }
            coVerify(exactly = 0) { fixture.stopTrackingSession.invoke() }
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    @Ignore("Still relies on immediate zoom changes")
    fun `dispatch should update exploration prototype values without invoking actions`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val initialVisibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val fixture = createFixture()

        try {
            fixture.viewModel.awaitContent()
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = visibleBoundsInside(initialVisibleRange),
                ),
            )
            advanceUntilIdle()

            // When
            fixture.viewModel.dispatch(MapIntent.CanonicalZoomChanged(value = 18))
            fixture.viewModel.dispatch(MapIntent.RevealRadiusMetersChanged(value = 42))
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent {
                it.debug.canonicalZoom == 18 && it.debug.revealRadiusMeters == 42
            }
            actual.debug.canonicalZoom shouldBe 18
            actual.debug.revealRadiusMeters shouldBe 42
            actual.fogOfWar.canonicalZoom shouldBe 18
            actual.fogOfWar.visibleTileRange?.zoom shouldBe 18
            coVerify(exactly = 0) { fixture.clearExploredTiles.invoke() }
            coVerify(exactly = 0) { fixture.startTrackingSession.invoke() }
            coVerify(exactly = 0) { fixture.stopTrackingSession.invoke() }
            verify(exactly = 1) { fixture.setExplorationTileCanonicalZoom.invoke(18) }
            verify(exactly = 1) { fixture.setExplorationTileRevealRadius.invoke(42.0) }
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should start exploration tracking automatically when map opens`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            startTrackingResult = StartExplorationTrackingSessionResult.Started,
        )

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.MapOpened)
            advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { fixture.startTrackingSession.invoke() }
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should request location permission when map open tracking start requires it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            startTrackingResult = StartExplorationTrackingSessionResult.PermissionRequired,
        )

        try {
            fixture.viewModel.awaitContent()
            val effectDeferred = async { fixture.viewModel.effects.first() }
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.MapOpened)
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe MapEffect.RequestLocationPermission
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should stop exploration tracking when stop tracking is clicked`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture()

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.StopTrackingClicked)
            advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { fixture.stopTrackingSession.invoke() }
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `uiState should reflect observed tracking session state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                cadence = ExplorationTrackingCadence.FOREGROUND,
                lastKnownLocation = GeoPoint(lat = 40.7128, lon = -74.0060),
            ),
        )

        try {
            // When
            val actual = fixture.viewModel.awaitContent { it.userLocation != null }

            // Then
            actual.isExplorationTrackingActive shouldBe true
            actual.explorationTrackingStatus shouldBe ExplorationTrackingStatus.TRACKING
            actual.explorationTrackingCadence shouldBe ExplorationTrackingCadence.FOREGROUND
            actual.userLocation shouldBe LatLng(latitude = 40.7128, longitude = -74.006)
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `uiState should follow updated user location by default`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            trackingSession = ExplorationTrackingSession(
                lastKnownLocation = GeoPoint(lat = 40.7128, lon = -74.0060),
            ),
        )

        try {
            val initial = fixture.viewModel.awaitContent {
                it.cameraPosition?.target == LatLng(latitude = 40.7128, longitude = -74.006)
            }

            // When
            fixture.trackingSessionFlow.value = fixture.trackingSessionFlow.value.copy(
                lastKnownLocation = GeoPoint(lat = 40.7306, lon = -73.9352),
            )
            advanceUntilIdle()

            // Then
            initial.cameraPosition?.target shouldBe LatLng(latitude = 40.7128, longitude = -74.006)
            val actual = fixture.viewModel.awaitContent {
                it.cameraPosition?.target == LatLng(latitude = 40.7306, longitude = -73.9352)
            }
            actual.cameraPosition?.target shouldBe LatLng(latitude = 40.7306, longitude = -73.9352)
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should stop following updated user location when camera settles from a user gesture`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            trackingSession = ExplorationTrackingSession(
                lastKnownLocation = GeoPoint(lat = 40.7128, lon = -74.0060),
            ),
        )
        val manualCameraPosition = CameraPositionState(
            target = LatLng(latitude = 40.7580, longitude = -73.9855),
            zoom = 15.0,
        )

        try {
            fixture.viewModel.awaitContent {
                it.cameraPosition?.target == LatLng(latitude = 40.7128, longitude = -74.006)
            }

            // When
            fixture.viewModel.dispatch(
                MapIntent.CameraSettled(
                    position = manualCameraPosition,
                    origin = CameraUpdateOrigin.USER,
                ),
            )
            advanceUntilIdle()
            fixture.trackingSessionFlow.value = fixture.trackingSessionFlow.value.copy(
                lastKnownLocation = GeoPoint(lat = 40.7306, lon = -73.9352),
            )
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent {
                it.cameraPosition == manualCameraPosition
            }
            actual.cameraPosition shouldBe manualCameraPosition
            actual.cameraUpdateOrigin shouldBe CameraUpdateOrigin.USER
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should reenable follow mode and increment recenter token when location permission is granted after recenter click`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))

            // Given
            val fixture = createFixture(
                trackingSession = ExplorationTrackingSession(
                    lastKnownLocation = GeoPoint(lat = 40.7128, lon = -74.0060),
                ),
                startTrackingResult = StartExplorationTrackingSessionResult.AlreadyActive,
            )
            val manualCameraPosition = CameraPositionState(
                target = LatLng(latitude = 40.7580, longitude = -73.9855),
                zoom = 15.0,
            )

            try {
                fixture.viewModel.awaitContent {
                    it.cameraPosition?.target == LatLng(latitude = 40.7128, longitude = -74.006)
                }
                fixture.viewModel.dispatch(
                    MapIntent.CameraSettled(
                        position = manualCameraPosition,
                        origin = CameraUpdateOrigin.USER,
                    ),
                )
                advanceUntilIdle()
                fixture.trackingSessionFlow.value = fixture.trackingSessionFlow.value.copy(
                    lastKnownLocation = GeoPoint(lat = 40.7306, lon = -73.9352),
                )
                advanceUntilIdle()
                fixture.viewModel.awaitContent { it.cameraPosition == manualCameraPosition }
                fixture.viewModel.dispatch(
                    MapIntent.MapViewportSizeChanged(
                        viewportSize = MapViewportSize(widthPx = 1080, heightPx = 1920),
                    ),
                )

                // When
                fixture.viewModel.dispatch(MapIntent.RecenterClicked)
                fixture.viewModel.dispatch(MapIntent.LocationPermissionResult(isGranted = true))
                advanceUntilIdle()

                // Then
                val actual = fixture.viewModel.awaitContent {
                    it.recenterRequestToken == 1 &&
                        it.cameraPosition?.target == LatLng(latitude = 40.7306, longitude = -73.9352)
                }
                actual.recenterRequestToken shouldBe 1
                actual.cameraPosition?.target shouldBe LatLng(latitude = 40.7306, longitude = -73.9352)
                actual.cameraUpdateOrigin shouldBe CameraUpdateOrigin.PROGRAMMATIC
                verify(exactly = 1) {
                    fixture.mapTilePrewarmer.prewarm(
                        match { request ->
                            request.style == fixture.mapStyle &&
                                request.currentCamera == manualCameraPosition &&
                                request.targetCamera == CameraPositionState(
                                    target = LatLng(latitude = 40.7306, longitude = -73.9352),
                                    zoom = manualCameraPosition.zoom,
                                ) &&
                                request.viewportSize == MapViewportSize(widthPx = 1080, heightPx = 1920) &&
                                request.sampleCount == 4 &&
                                request.burstLimit == 96
                        },
                    )
                }
            } finally {
                tearDownMainDispatcher()
            }
        }

    @Test
    fun `dispatch should emit denial message when location permission is denied after recenter click`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture()

        try {
            fixture.viewModel.awaitContent()
            val effectDeferred = async {
                fixture.viewModel.effects
                    .first { it is MapEffect.ShowMessage }
            }
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.RecenterClicked)
            fixture.viewModel.dispatch(MapIntent.LocationPermissionResult(isGranted = false))
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe MapEffect.ShowMessage(
                message = "Location permission is required to center the map on your position.",
            )
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should derive fog overlay stats when the viewport changes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val fixture = createFixture(
            exploredTiles = setOf(
                ExplorationTile(
                    zoom = visibleRange.zoom,
                    x = 10,
                    y = 20,
                ),
            ),
        )

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = visibleBoundsInside(visibleRange),
                ),
            )
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent { it.fogOfWar.exploredVisibleTileCount == 1 }
            actual.fogOfWar.visibleTileCount shouldBe 4L
            actual.fogOfWar.exploredVisibleTileCount shouldBe 1
            actual.fogOfWar.activeRenderData.shouldNotBeNull()
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should avoid fog recomputation while the viewport stays inside the trigger bounds`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val initialVisibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val shiftedVisibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            minX = 11,
            maxX = 12,
            minY = 20,
            maxY = 21,
        )
        val fixture = createFixture(
            observeExploredTilesFlowFactory = { MutableStateFlow(emptySet()) },
        )

        try {
            fixture.viewModel.awaitContent()
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = visibleBoundsInside(initialVisibleRange),
                ),
            )
            advanceTimeBy(1_000L)
            advanceUntilIdle()
            fixture.viewModel.awaitContent { it.fogOfWar.visibleTileRange == initialVisibleRange }

            // When
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = visibleBoundsInside(shiftedVisibleRange),
                ),
            )
            runCurrent()

            // Then
            val actual = fixture.viewModel.awaitContent {
                it.fogOfWar.visibleTileRange == shiftedVisibleRange
            }
            actual.fogOfWar.activeRenderData.shouldNotBeNull()
            actual.fogOfWar.isRecomputing shouldBe false
            verify(exactly = 1) { fixture.observeExploredTiles.invoke(any()) }
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should keep active fog and expose handoff fog while pending exact fog is still loading`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val initialVisibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val outrunVisibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            minX = 14,
            maxX = 15,
            minY = 20,
            maxY = 21,
        )
        val fixture = createFixture(
            observeExploredTilesFlowFactory = { range ->
                when (range) {
                    expectedFogBufferRange(initialVisibleRange) -> MutableStateFlow(emptySet())
                    expectedFogBufferRange(outrunVisibleRange) -> flow {
                        delay(1_000L)
                        emit(emptySet())
                    }

                    else -> MutableStateFlow(emptySet())
                }
            },
        )

        try {
            fixture.viewModel.awaitContent()
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = visibleBoundsInside(initialVisibleRange),
                ),
            )
            advanceUntilIdle()

            // When
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = visibleBoundsInside(outrunVisibleRange),
                ),
            )
            runCurrent()

            // Then
            val actual = fixture.viewModel.awaitContent {
                it.fogOfWar.visibleTileRange == outrunVisibleRange
            }
            actual.fogOfWar.activeRenderData.shouldNotBeNull()
            actual.fogOfWar.handoffRenderData.shouldNotBeNull()
            actual.fogOfWar.isRecomputing shouldBe true
            actual.fogOfWar.bufferedBounds shouldBe expectedFogBufferBounds(initialVisibleRange)
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should keep the newest handoff fog and avoid swapping stale pending buffers during repeated fast pans`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))

            // Given
            val initialVisibleRange = ExplorationTileRange(
                zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
                minX = 10,
                maxX = 11,
                minY = 20,
                maxY = 21,
            )
            val secondVisibleRange = ExplorationTileRange(
                zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
                minX = 14,
                maxX = 15,
                minY = 20,
                maxY = 21,
            )
            val thirdVisibleRange = ExplorationTileRange(
                zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
                minX = 18,
                maxX = 19,
                minY = 20,
                maxY = 21,
            )
            val fixture = createFixture(
                observeExploredTilesFlowFactory = { range ->
                    when (range) {
                        expectedFogBufferRange(initialVisibleRange) -> MutableStateFlow(emptySet())
                        expectedFogBufferRange(secondVisibleRange),
                        expectedFogBufferRange(thirdVisibleRange) -> flow {
                            delay(1_000L)
                            emit(emptySet())
                        }

                        else -> MutableStateFlow(emptySet())
                    }
                },
            )

            try {
                fixture.viewModel.awaitContent()
                fixture.viewModel.dispatch(
                    MapIntent.CameraViewportChanged(
                        visibleBounds = visibleBoundsInside(initialVisibleRange),
                    ),
                )
                advanceUntilIdle()

                fixture.viewModel.dispatch(
                    MapIntent.CameraViewportChanged(
                        visibleBounds = visibleBoundsInside(secondVisibleRange),
                    ),
                )
                runCurrent()
                fixture.viewModel.dispatch(
                    MapIntent.CameraViewportChanged(
                        visibleBounds = visibleBoundsInside(thirdVisibleRange),
                    ),
                )
                runCurrent()

                // When
                advanceTimeBy(1_000L)
                runCurrent()

                // Then
                val pending = fixture.viewModel.awaitContent {
                    it.fogOfWar.visibleTileRange == thirdVisibleRange
                }
                pending.fogOfWar.bufferedBounds shouldBe expectedFogBufferBounds(initialVisibleRange)
                pending.fogOfWar.handoffRenderData.shouldNotBeNull()
                pending.fogOfWar.isRecomputing shouldBe true

                advanceTimeBy(1_000L)
                advanceUntilIdle()

                val actual = fixture.viewModel.awaitContent {
                    it.fogOfWar.bufferedBounds == expectedFogBufferBounds(thirdVisibleRange)
                }
                actual.fogOfWar.activeRenderData.shouldNotBeNull()
                actual.fogOfWar.handoffRenderData shouldBe null
                actual.fogOfWar.isRecomputing shouldBe false
            } finally {
                tearDownMainDispatcher()
            }
        }

    @Test
    fun `dispatch should recompute exact fog after a pending buffer swap when explored tiles change`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val initialVisibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val shiftedVisibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            minX = 14,
            maxX = 15,
            minY = 20,
            maxY = 21,
        )
        val shiftedTile = ExplorationTile(
            zoom = shiftedVisibleRange.zoom,
            x = shiftedVisibleRange.minX,
            y = shiftedVisibleRange.minY,
        )
        val shiftedFlow = MutableStateFlow(emptySet<ExplorationTile>())
        val fixture = createFixture(
            observeExploredTilesFlowFactory = { range ->
                when (range) {
                    expectedFogBufferRange(initialVisibleRange) -> MutableStateFlow(emptySet())
                    expectedFogBufferRange(shiftedVisibleRange) -> shiftedFlow
                    else -> MutableStateFlow(emptySet())
                }
            },
        )

        try {
            fixture.viewModel.awaitContent()
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = visibleBoundsInside(initialVisibleRange),
                ),
            )
            advanceUntilIdle()

            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = visibleBoundsInside(shiftedVisibleRange),
                ),
            )
            advanceUntilIdle()

            // When
            shiftedFlow.value = setOf(shiftedTile)
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent {
                it.fogOfWar.bufferedBounds == expectedFogBufferBounds(shiftedVisibleRange) &&
                    it.fogOfWar.exploredVisibleTileCount == 1
            }
            actual.fogOfWar.exploredVisibleTileCount shouldBe 1
            actual.fogOfWar.handoffRenderData shouldBe null
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should reuse buffered resource query when the viewport changes inside the current resource bounds`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))

            // Given
            val initialVisibleRange = ExplorationTileRange(
                zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
                minX = 10,
                maxX = 11,
                minY = 20,
                maxY = 21,
            )
            val shiftedVisibleRange = ExplorationTileRange(
                zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
                minX = 11,
                maxX = 12,
                minY = 20,
                maxY = 21,
            )
            val fixture = createFixture(
                resourceSpawns = listOf(
                    resourceSpawn(
                        id = "cell-1-slot-0",
                        resourceTypeId = "wood",
                        lat = 50.46,
                        lon = 30.53,
                        collectionRadiusMeters = 24.0,
                    ),
                ),
            )

            try {
                fixture.viewModel.awaitContent()
                fixture.viewModel.dispatch(
                    MapIntent.CameraViewportChanged(
                        visibleBounds = visibleBoundsInside(initialVisibleRange),
                    ),
                )
                advanceUntilIdle()

                fixture.viewModel.dispatch(
                    MapIntent.CameraViewportChanged(
                        visibleBounds = visibleBoundsInside(shiftedVisibleRange),
                    ),
                )
                runCurrent()

                verify(exactly = 1) { fixture.observeCollectibleResourceSpawns.invoke(any()) }
            } finally {
                tearDownMainDispatcher()
            }
        }

    @Test
    @Ignore("Still relies on immediate zoom changes")
    fun `uiState should emit current canonical zoom immediately when precise fog data is still loading`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val initialVisibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val fixture = createFixture(
            observeExploredTilesFlowFactory = { range ->
                when (range.zoom) {
                    ExplorationTilePrototype.CANONICAL_ZOOM -> MutableStateFlow(emptySet())
                    18 -> flow {
                        delay(1_000L)
                        emit(emptySet())
                    }

                    else -> MutableStateFlow(emptySet())
                }
            },
        )

        try {
            fixture.viewModel.awaitContent()
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = visibleBoundsInside(initialVisibleRange),
                ),
            )
            advanceUntilIdle()
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.CanonicalZoomChanged(value = 18))
            runCurrent()
            advanceTimeBy(999L)
            runCurrent()

            // Then
            val pending = fixture.viewModel.awaitContent { it.debug.canonicalZoom == 18 }
            pending.fogOfWar.canonicalZoom shouldBe 18
            pending.fogOfWar.visibleTileRange?.zoom shouldBe 18
            pending.fogOfWar.fogRanges.all { it.zoom == 18 } shouldBe true
            pending.fogOfWar.activeRenderData.shouldNotBeNull()

            advanceTimeBy(1L)
            advanceUntilIdle()

            val actual = fixture.viewModel.awaitContent { it.debug.canonicalZoom == 18 }
            actual.fogOfWar.canonicalZoom shouldBe 18
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should keep tapped map position after user location updates`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            trackingSession = ExplorationTrackingSession(
                lastKnownLocation = GeoPoint(lat = 40.7128, lon = -74.0060),
            ),
        )
        val tappedLocation = LatLng(latitude = 40.7580, longitude = -73.9855)

        try {
            val initial = fixture.viewModel.awaitContent {
                it.cameraPosition?.target == LatLng(latitude = 40.7128, longitude = -74.006)
            }
            fixture.viewModel.dispatch(
                MapIntent.MapViewportSizeChanged(
                    viewportSize = MapViewportSize(widthPx = 1080, heightPx = 1920),
                ),
            )

            // When
            fixture.viewModel.dispatch(MapIntent.MapTapped(target = tappedLocation))
            advanceUntilIdle()
            fixture.trackingSessionFlow.value = fixture.trackingSessionFlow.value.copy(
                lastKnownLocation = GeoPoint(lat = 40.7306, lon = -73.9352),
            )
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent {
                it.cameraPosition?.target == tappedLocation
            }
            actual.cameraPosition?.target shouldBe tappedLocation
            actual.cameraUpdateOrigin shouldBe CameraUpdateOrigin.PROGRAMMATIC
            verify(exactly = 1) {
                fixture.mapTilePrewarmer.prewarm(
                    match { request ->
                        request.style == fixture.mapStyle &&
                            request.currentCamera == initial.cameraPosition &&
                            request.targetCamera == CameraPositionState(
                                target = tappedLocation,
                                zoom = initial.cameraPosition.shouldNotBeNull().zoom,
                            ) &&
                            request.viewportSize == MapViewportSize(widthPx = 1080, heightPx = 1920) &&
                            request.sampleCount == 1 &&
                            request.burstLimit == 48
                    },
                )
            }
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should mark camera as user controlled when gesture starts after programmatic move`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            trackingSession = ExplorationTrackingSession(
                lastKnownLocation = GeoPoint(lat = 40.7128, lon = -74.0060),
            ),
        )
        val tappedLocation = LatLng(latitude = 40.7580, longitude = -73.9855)
        val gestureCameraPosition = CameraPositionState(
            target = LatLng(latitude = 40.7615, longitude = -73.9777),
            zoom = 15.5,
        )

        try {
            fixture.viewModel.awaitContent {
                it.cameraPosition?.target == LatLng(latitude = 40.7128, longitude = -74.006)
            }
            fixture.viewModel.dispatch(MapIntent.MapTapped(target = tappedLocation))
            advanceUntilIdle()

            // When
            fixture.viewModel.dispatch(
                MapIntent.CameraGestureStarted(
                    position = gestureCameraPosition,
                ),
            )
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent {
                it.cameraPosition == gestureCameraPosition
            }
            actual.cameraPosition shouldBe gestureCameraPosition
            actual.cameraUpdateOrigin shouldBe CameraUpdateOrigin.USER
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should recenter camera and open object details when tapped object is present`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            trackingSession = ExplorationTrackingSession(
                lastKnownLocation = GeoPoint(lat = 40.7128, lon = -74.0060),
            ),
            pointsOfInterest = listOf(
                pointOfInterest(id = FIRST_POI_ID, lat = 51.1, lon = 17.03),
            ),
        )

        try {
            fixture.viewModel.awaitContent()
            val effectDeferred = async { fixture.viewModel.effects.first() }
            runCurrent()

            // When
            fixture.viewModel.dispatch(
                MapIntent.ObjectTapped(
                    objectId = "${POI_ID_PREFIX}:$FIRST_POI_ID",
                ),
            )
            advanceUntilIdle()
            fixture.trackingSessionFlow.value = fixture.trackingSessionFlow.value.copy(
                lastKnownLocation = GeoPoint(lat = 40.7306, lon = -73.9352),
            )
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe MapEffect.OpenObjectDetails(objectId = FIRST_POI_ID.toString())
            coVerify(exactly = 1) { fixture.discoverPointOfInterest.invoke(FIRST_POI_ID) }

            val actual = fixture.viewModel.awaitContent {
                it.cameraPosition?.target == LatLng(latitude = 51.1, longitude = 17.03)
            }
            actual.cameraPosition?.target shouldBe LatLng(latitude = 51.1, longitude = 17.03)
            actual.cameraUpdateOrigin shouldBe CameraUpdateOrigin.PROGRAMMATIC
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should recenter camera and not open POI details when tapped object is a resource spawn`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val spawnId = "cell-2-slot-1"
        val fixture = createFixture(
            trackingSession = ExplorationTrackingSession(
                lastKnownLocation = GeoPoint(lat = 40.7128, lon = -74.0060),
            ),
            pointsOfInterest = emptyList(),
            resourceSpawns = listOf(
                resourceSpawn(
                    id = spawnId,
                    resourceTypeId = "stone",
                    lat = 51.2,
                    lon = 17.1,
                    collectionRadiusMeters = 20.0,
                ),
            ),
        )
        val visibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
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
            fixture.viewModel.awaitContent {
                it.visibleObjects.any { objectModel ->
                    objectModel.id == "${RESOURCE_SPAWN_ID_PREFIX}:$spawnId"
                }
            }

            // When
            fixture.viewModel.dispatch(
                MapIntent.ObjectTapped(
                    objectId = "${RESOURCE_SPAWN_ID_PREFIX}:$spawnId",
                ),
            )
            advanceUntilIdle()

            // Then
            coVerify(exactly = 0) { fixture.discoverPointOfInterest.invoke(any()) }
            val actual = fixture.viewModel.awaitContent {
                it.cameraPosition?.target == LatLng(latitude = 51.2, longitude = 17.1)
            }
            actual.cameraPosition?.target shouldBe LatLng(latitude = 51.2, longitude = 17.1)
            actual.cameraUpdateOrigin shouldBe CameraUpdateOrigin.PROGRAMMATIC
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should clear explored tiles when prototype clear action is tapped`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture()

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.ResetExploredTilesClicked)
            advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { fixture.clearExploredTiles.invoke() }
        } finally {
            tearDownMainDispatcher()
        }
    }

    private suspend fun MapViewModel.awaitContent(
        predicate: (MapUiState.Content) -> Boolean = { true },
    ): MapUiState.Content = uiState
        .mapNotNull { it as? MapUiState.Content }
        .first(predicate)

    private suspend fun MapViewModel.awaitFailure(): MapUiState.Failure =
        uiState.first { it is MapUiState.Failure } as MapUiState.Failure

    private fun TestScope.tearDownMainDispatcher() {
        advanceUntilIdle()
        advanceTimeBy(5_001L)
        runCurrent()
        Dispatchers.resetMain()
    }

    private data class Fixture(
        val viewModel: MapViewModel,
        val mapStyle: MapStyle,
        val trackingSessionFlow: MutableStateFlow<ExplorationTrackingSession>,
        val mapTilePrewarmer: MapTilePrewarmer,
        val observeCollectibleResourceSpawns: ObserveCollectibleResourceSpawnsUseCase,
        val observeExploredTiles: ObserveExploredTilesUseCase,
        val discoverPointOfInterest: DiscoverPointOfInterestUseCase,
        val clearExploredTiles: ClearExploredTilesUseCase,
        val getExploredTiles: GetExploredTilesUseCase,
        val setExplorationTileCanonicalZoom: SetExplorationTileCanonicalZoomUseCase,
        val setExplorationTileRevealRadius: SetExplorationTileRevealRadiusUseCase,
        val startTrackingSession: StartExplorationTrackingSessionUseCase,
        val stopTrackingSession: StopExplorationTrackingSessionUseCase,
    )

    private fun createFixture(
        mapStyleOutput: Output<MapStyle?, DomainError>? = null,
        pointsOfInterest: List<PointOfInterest> = listOf(
            pointOfInterest(id = FIRST_POI_ID, lat = 50.45, lon = 30.52),
        ),
        resourceSpawns: List<ResourceSpawn> = emptyList(),
        explorationProgress: ExplorationProgress = ExplorationProgress(discovered = emptySet()),
        exploredTiles: Set<ExplorationTile> = emptySet(),
        observeExploredTilesFlowFactory: ((ExplorationTileRange) -> Flow<Set<ExplorationTile>>)? = null,
        trackingSession: ExplorationTrackingSession = ExplorationTrackingSession(),
        tileRuntimeConfig: ExplorationTileRuntimeConfig = ExplorationTileRuntimeConfig(),
        startTrackingResult: StartExplorationTrackingSessionResult =
            StartExplorationTrackingSessionResult.AlreadyActive,
    ): Fixture {
        val observePointsOfInterest = mockk<ObservePointsOfInterestUseCase>()
        val observeCollectibleResourceSpawns = mockk<ObserveCollectibleResourceSpawnsUseCase>()
        val observeExplorationProgress = mockk<ObserveExplorationProgressUseCase>()
        val observeExploredTiles = mockk<ObserveExploredTilesUseCase>()
        val observeSelectedMapStyle = mockk<ObserveSelectedMapStyleUseCase>()
        val discoverPointOfInterest = mockk<DiscoverPointOfInterestUseCase>()
        val clearExploredTiles = mockk<ClearExploredTilesUseCase>()
        val getExploredTiles = mockk<GetExploredTilesUseCase>()
        val getExplorationTileRuntimeConfig = mockk<GetExplorationTileRuntimeConfigUseCase>()
        val observeExplorationTileRuntimeConfig = mockk<ObserveExplorationTileRuntimeConfigUseCase>()
        val setExplorationTileCanonicalZoom = mockk<SetExplorationTileCanonicalZoomUseCase>()
        val setExplorationTileRevealRadius = mockk<SetExplorationTileRevealRadiusUseCase>()
        val observeExplorationTrackingSession = mockk<ObserveExplorationTrackingSessionUseCase>()
        val startTrackingSession = mockk<StartExplorationTrackingSessionUseCase>()
        val stopTrackingSession = mockk<StopExplorationTrackingSessionUseCase>()
        val mapTilePrewarmer = mockk<MapTilePrewarmer>()

        val mapStyle = MapStyle.remote(
            id = "style-remote",
            name = "Remote",
            value = "https://example.com/style.json",
        )
        val trackingSessionFlow = MutableStateFlow(trackingSession)

        every { observePointsOfInterest.invoke() } returns MutableStateFlow(pointsOfInterest)
        every { observeCollectibleResourceSpawns.invoke(any()) } returns MutableStateFlow(resourceSpawns)
        every { observeExplorationProgress.invoke() } returns MutableStateFlow(explorationProgress)
        every { observeExploredTiles.invoke(any()) } answers {
            observeExploredTilesFlowFactory?.invoke(arg(0))
                ?: MutableStateFlow(exploredTiles)
        }
        every { observeSelectedMapStyle.invoke() } returns MutableStateFlow(
            mapStyleOutput ?: Output.Success(mapStyle),
        )
        every { getExplorationTileRuntimeConfig.invoke() } returns tileRuntimeConfig
        every { observeExplorationTileRuntimeConfig.invoke() } returns MutableStateFlow(tileRuntimeConfig)
        every { observeExplorationTrackingSession.invoke() } returns trackingSessionFlow
        every { setExplorationTileCanonicalZoom.invoke(any()) } just runs
        every { setExplorationTileRevealRadius.invoke(any()) } just runs
        every { mapTilePrewarmer.prewarm(any()) } returns Job()
        coEvery { getExploredTiles.invoke(any()) } coAnswers {
            observeExploredTilesFlowFactory?.invoke(arg(0))?.first()
                ?: exploredTiles
        }

        coEvery { discoverPointOfInterest.invoke(any()) } just runs
        coEvery { clearExploredTiles.invoke() } just runs
        coEvery { startTrackingSession.invoke() } returns startTrackingResult
        coEvery { stopTrackingSession.invoke() } just runs

        return Fixture(
            viewModel = MapViewModel(
                observePointsOfInterest = observePointsOfInterest,
                observeCollectibleResourceSpawns = observeCollectibleResourceSpawns,
                observeExplorationProgress = observeExplorationProgress,
                observeSelectedMapStyle = observeSelectedMapStyle,
                discoverPointOfInterest = discoverPointOfInterest,
                clearExploredTiles = clearExploredTiles,
                getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfig,
                setExplorationTileCanonicalZoom = setExplorationTileCanonicalZoom,
                setExplorationTileRevealRadius = setExplorationTileRevealRadius,
                fogOfWarControllerFactory = { scope ->
                    FogOfWarController(
                        observeExplorationTileRuntimeConfig = observeExplorationTileRuntimeConfig,
                        observeExploredTiles = observeExploredTiles,
                        getExploredTiles = getExploredTiles,
                        renderDataFactory = FowRenderDataFactory(),
                        fogOfWarCalculator = FogOfWarCalculator(),
                        scope = scope,
                    )
                },
                observeExplorationTrackingSession = observeExplorationTrackingSession,
                startExplorationTrackingSession = startTrackingSession,
                stopExplorationTrackingSession = stopTrackingSession,
                mapTilePrewarmer = mapTilePrewarmer,
            ),
            mapStyle = mapStyle,
            trackingSessionFlow = trackingSessionFlow,
            mapTilePrewarmer = mapTilePrewarmer,
            observeCollectibleResourceSpawns = observeCollectibleResourceSpawns,
            observeExploredTiles = observeExploredTiles,
            discoverPointOfInterest = discoverPointOfInterest,
            clearExploredTiles = clearExploredTiles,
            getExploredTiles = getExploredTiles,
            setExplorationTileCanonicalZoom = setExplorationTileCanonicalZoom,
            setExplorationTileRevealRadius = setExplorationTileRevealRadius,
            startTrackingSession = startTrackingSession,
            stopTrackingSession = stopTrackingSession,
        )
    }

    private fun pointOfInterest(
        id: Long,
        lat: Double,
        lon: Double,
    ): PointOfInterest = PointOfInterest(
        id = id,
        name = "Point $id",
        description = "Description $id",
        category = PoiCategory.LANDMARK,
        location = GeoPoint(lat = lat, lon = lon),
        radiusMeters = 100,
    )

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
        ExplorationTileGrid.bounds(range).let { bounds ->
            GeoBounds(
                south = bounds.south + VIEWPORT_BOUNDS_EPSILON,
                west = bounds.west + VIEWPORT_BOUNDS_EPSILON,
                north = bounds.north - VIEWPORT_BOUNDS_EPSILON,
                east = bounds.east - VIEWPORT_BOUNDS_EPSILON,
            )
        }

    private fun expectedFogBufferRange(range: ExplorationTileRange): ExplorationTileRange {
        val widthInTiles = range.maxX - range.minX + 1
        val heightInTiles = range.maxY - range.minY + 1
        val triggerMultiplier = (sqrt(5.0) - 1.0) / 2.0
        val bufferedMultiplier = (sqrt(10.0) - 1.0) / 2.0
        val triggerHorizontalPadding = ceil(widthInTiles * triggerMultiplier).toInt().coerceAtLeast(1)
        val triggerVerticalPadding = ceil(heightInTiles * triggerMultiplier).toInt().coerceAtLeast(1)
        val bufferedHorizontalPadding = maxOf(
            triggerHorizontalPadding + 1,
            ceil(widthInTiles * bufferedMultiplier).toInt().coerceAtLeast(1),
        )
        val bufferedVerticalPadding = maxOf(
            triggerVerticalPadding + 1,
            ceil(heightInTiles * bufferedMultiplier).toInt().coerceAtLeast(1),
        )

        return range.expandedBy(
            horizontalTilePadding = bufferedHorizontalPadding,
            verticalTilePadding = bufferedVerticalPadding,
        )
    }

    private fun expectedFogBufferBounds(range: ExplorationTileRange): GeoBounds =
        ExplorationTileGrid.bounds(expectedFogBufferRange(range))

    private data class TestDomainError(
        override val message: String? = null,
        override val cause: Throwable? = null,
    ) : DomainError

    private companion object {
        const val VIEWPORT_BOUNDS_EPSILON = 1e-6
        const val FIRST_POI_ID = 1L
        const val SECOND_POI_ID = 2L
        const val POI_ID_PREFIX = "poi"
        const val RESOURCE_SPAWN_ID_PREFIX = "spawn"
    }
}
