package com.github.arhor.journey.feature.map

import com.github.arhor.journey.core.common.DomainError
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.DiscoveredPoi
import com.github.arhor.journey.domain.model.ExplorationProgress
import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileGrid
import com.github.arhor.journey.domain.model.ExplorationTilePrototype
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.PoiCategory
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.model.StartExplorationTrackingSessionResult
import com.github.arhor.journey.domain.usecase.ClearExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.usecase.GetExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObserveExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.SetExplorationTileCanonicalZoomUseCase
import com.github.arhor.journey.domain.usecase.SetExplorationTileRevealRadiusUseCase
import com.github.arhor.journey.domain.usecase.StartExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.StopExplorationTrackingSessionUseCase
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import java.time.Instant

class MapViewModelTest {

    @Test
    fun `uiState should map discovery flags when exploration progress contains discovered points of interest`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val pointsOfInterest = listOf(
            pointOfInterest(id = "poi-1", lat = 49.0, lon = 24.0),
            pointOfInterest(id = "poi-2", lat = 49.1, lon = 24.1),
        )
        val fixture = createFixture(
            pointsOfInterest = pointsOfInterest,
            explorationProgress = ExplorationProgress(
                discovered = setOf(
                    DiscoveredPoi(
                        poiId = "poi-1",
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
                "poi-1" to true,
                "poi-2" to false,
            )
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
            actual.debug.isFogOfWarOverlayEnabled shouldBe true
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
                !it.debug.isFogOfWarOverlayEnabled &&
                    it.debug.isTilesGridOverlayEnabled &&
                    it.debug.renderMode == MapRenderMode.Debug
            }
            actual.debug.isFogOfWarOverlayEnabled shouldBe false
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
    fun `dispatch should reenable follow mode and increment recenter token when location permission is granted after recenter click`() = runTest {
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
            val actual = fixture.viewModel.awaitContent { it.fogOfWar.visibleTileCount == 4L }
            actual.fogOfWar.visibleTileCount shouldBe 4L
            actual.fogOfWar.exploredVisibleTileCount shouldBe 1
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
            fixture.viewModel.awaitContent {
                it.cameraPosition?.target == LatLng(latitude = 40.7128, longitude = -74.006)
            }

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
                pointOfInterest(id = "poi-1", lat = 51.1, lon = 17.03),
            ),
        )

        try {
            fixture.viewModel.awaitContent()
            val effectDeferred = async { fixture.viewModel.effects.first() }
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.ObjectTapped(objectId = "poi-1"))
            advanceUntilIdle()
            fixture.trackingSessionFlow.value = fixture.trackingSessionFlow.value.copy(
                lastKnownLocation = GeoPoint(lat = 40.7306, lon = -73.9352),
            )
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe MapEffect.OpenObjectDetails(objectId = "poi-1")
            coVerify(exactly = 1) { fixture.discoverPointOfInterest.invoke("poi-1") }

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
        val discoverPointOfInterest: DiscoverPointOfInterestUseCase,
        val clearExploredTiles: ClearExploredTilesUseCase,
        val setExplorationTileCanonicalZoom: SetExplorationTileCanonicalZoomUseCase,
        val setExplorationTileRevealRadius: SetExplorationTileRevealRadiusUseCase,
        val startTrackingSession: StartExplorationTrackingSessionUseCase,
        val stopTrackingSession: StopExplorationTrackingSessionUseCase,
    )

    private fun createFixture(
        mapStyleOutput: Output<MapStyle?, DomainError>? = null,
        pointsOfInterest: List<PointOfInterest> = listOf(
            pointOfInterest(id = "poi-1", lat = 50.45, lon = 30.52),
        ),
        explorationProgress: ExplorationProgress = ExplorationProgress(discovered = emptySet()),
        exploredTiles: Set<ExplorationTile> = emptySet(),
        trackingSession: ExplorationTrackingSession = ExplorationTrackingSession(),
        tileRuntimeConfig: ExplorationTileRuntimeConfig = ExplorationTileRuntimeConfig(),
        startTrackingResult: StartExplorationTrackingSessionResult =
            StartExplorationTrackingSessionResult.AlreadyActive,
    ): Fixture {
        val observePointsOfInterest = mockk<ObservePointsOfInterestUseCase>()
        val observeExplorationProgress = mockk<ObserveExplorationProgressUseCase>()
        val observeExploredTiles = mockk<ObserveExploredTilesUseCase>()
        val observeSelectedMapStyle = mockk<ObserveSelectedMapStyleUseCase>()
        val discoverPointOfInterest = mockk<DiscoverPointOfInterestUseCase>()
        val clearExploredTiles = mockk<ClearExploredTilesUseCase>()
        val getExplorationTileRuntimeConfig = mockk<GetExplorationTileRuntimeConfigUseCase>()
        val setExplorationTileCanonicalZoom = mockk<SetExplorationTileCanonicalZoomUseCase>()
        val setExplorationTileRevealRadius = mockk<SetExplorationTileRevealRadiusUseCase>()
        val observeExplorationTrackingSession = mockk<ObserveExplorationTrackingSessionUseCase>()
        val startTrackingSession = mockk<StartExplorationTrackingSessionUseCase>()
        val stopTrackingSession = mockk<StopExplorationTrackingSessionUseCase>()

        val mapStyle = MapStyle.remote(
            id = "style-remote",
            name = "Remote",
            value = "https://example.com/style.json",
        )
        val trackingSessionFlow = MutableStateFlow(trackingSession)

        every { observePointsOfInterest.invoke() } returns MutableStateFlow(pointsOfInterest)
        every { observeExplorationProgress.invoke() } returns MutableStateFlow(explorationProgress)
        every { observeExploredTiles.invoke(any()) } returns MutableStateFlow(exploredTiles)
        every { observeSelectedMapStyle.invoke() } returns MutableStateFlow(
            mapStyleOutput ?: Output.Success(mapStyle),
        )
        every { getExplorationTileRuntimeConfig.invoke() } returns tileRuntimeConfig
        every { observeExplorationTrackingSession.invoke() } returns trackingSessionFlow
        every { setExplorationTileCanonicalZoom.invoke(any()) } just runs
        every { setExplorationTileRevealRadius.invoke(any()) } just runs

        coEvery { discoverPointOfInterest.invoke(any()) } just runs
        coEvery { clearExploredTiles.invoke() } just runs
        coEvery { startTrackingSession.invoke() } returns startTrackingResult
        coEvery { stopTrackingSession.invoke() } just runs

        return Fixture(
            viewModel = MapViewModel(
                observePointsOfInterest = observePointsOfInterest,
                observeExplorationProgress = observeExplorationProgress,
                observeExploredTiles = observeExploredTiles,
                observeSelectedMapStyle = observeSelectedMapStyle,
                discoverPointOfInterest = discoverPointOfInterest,
                clearExploredTiles = clearExploredTiles,
                getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfig,
                setExplorationTileCanonicalZoom = setExplorationTileCanonicalZoom,
                setExplorationTileRevealRadius = setExplorationTileRevealRadius,
                observeExplorationTrackingSession = observeExplorationTrackingSession,
                startExplorationTrackingSession = startTrackingSession,
                stopExplorationTrackingSession = stopTrackingSession,
            ),
            mapStyle = mapStyle,
            trackingSessionFlow = trackingSessionFlow,
            discoverPointOfInterest = discoverPointOfInterest,
            clearExploredTiles = clearExploredTiles,
            setExplorationTileCanonicalZoom = setExplorationTileCanonicalZoom,
            setExplorationTileRevealRadius = setExplorationTileRevealRadius,
            startTrackingSession = startTrackingSession,
            stopTrackingSession = stopTrackingSession,
        )
    }

    private fun pointOfInterest(
        id: String,
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

    private fun visibleBoundsInside(range: ExplorationTileRange): GeoBounds =
        ExplorationTileGrid.bounds(range).let { bounds ->
            GeoBounds(
                south = bounds.south + VIEWPORT_BOUNDS_EPSILON,
                west = bounds.west + VIEWPORT_BOUNDS_EPSILON,
                north = bounds.north - VIEWPORT_BOUNDS_EPSILON,
                east = bounds.east - VIEWPORT_BOUNDS_EPSILON,
            )
        }

    private data class TestDomainError(
        override val message: String? = null,
        override val cause: Throwable? = null,
    ) : DomainError

    private companion object {
        const val VIEWPORT_BOUNDS_EPSILON = 1e-6
    }
}
