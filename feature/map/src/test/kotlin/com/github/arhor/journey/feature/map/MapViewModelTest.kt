package com.github.arhor.journey.feature.map

import com.github.arhor.journey.core.common.DomainError
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.DiscoveredPoi
import com.github.arhor.journey.domain.model.ExplorationProgress
import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileGrid
import com.github.arhor.journey.domain.model.ExplorationTilePrototype
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.PoiCategory
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.usecase.ClearExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.usecase.ObserveExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.RevealExplorationTilesAtLocationUseCase
import com.github.arhor.journey.feature.map.location.ForegroundUserLocationTracker
import com.github.arhor.journey.feature.map.location.UserLocationUpdate
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
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
    fun `uiState should not expose a startup camera before the first location fix`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture()

        try {
            // When
            val actual = fixture.viewModel.awaitContent()

            // Then
            actual.cameraPosition shouldBe null
        } finally {
            tearDownMainDispatcher()
        }
    }

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
    fun `uiState should emit fallback failure when selected map style output fails without message and cause`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            mapStyleOutput = Output.Failure(error = TestDomainError()),
        )

        try {
            // When
            val actual = fixture.viewModel.awaitFailure()

            // Then
            actual shouldBe MapUiState.Failure(errorMessage = "Failed to load settings state.")
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `uiState should emit failure when selected map style flow throws exception`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val observePointsOfInterest = mockk<ObservePointsOfInterestUseCase>()
        val observeExplorationProgress = mockk<ObserveExplorationProgressUseCase>()
        val observeExploredTiles = mockk<ObserveExploredTilesUseCase>()
        val observeSelectedMapStyle = mockk<ObserveSelectedMapStyleUseCase>()
        val discoverPointOfInterest = mockk<DiscoverPointOfInterestUseCase>()
        val revealExplorationTilesAtLocation = mockk<RevealExplorationTilesAtLocationUseCase>()
        val clearExploredTiles = mockk<ClearExploredTilesUseCase>()
        val foregroundUserLocationTracker = mockk<ForegroundUserLocationTracker>()

        every { observePointsOfInterest.invoke() } returns flowOf(emptyList())
        every { observeExplorationProgress.invoke() } returns flowOf(ExplorationProgress(discovered = emptySet()))
        every { observeExploredTiles.invoke(any()) } returns flowOf(emptySet())
        every { observeSelectedMapStyle.invoke() } returns flow {
            throw IllegalStateException("Broken map style stream.")
        }
        coEvery { discoverPointOfInterest.invoke(any()) } just runs
        coEvery { revealExplorationTilesAtLocation.invoke(any()) } returns emptySet()
        coEvery { clearExploredTiles.invoke() } just runs
        every { foregroundUserLocationTracker.observeLocations() } returns flowOf(UserLocationUpdate.TemporarilyUnavailable)

        val viewModel = MapViewModel(
            observePointsOfInterest = observePointsOfInterest,
            observeExplorationProgress = observeExplorationProgress,
            observeExploredTiles = observeExploredTiles,
            observeSelectedMapStyle = observeSelectedMapStyle,
            discoverPointOfInterest = discoverPointOfInterest,
            revealExplorationTilesAtLocation = revealExplorationTilesAtLocation,
            clearExploredTiles = clearExploredTiles,
            foregroundUserLocationTracker = foregroundUserLocationTracker,
        )

        try {
            // When
            val actual = viewModel.awaitFailure()

            // Then
            actual shouldBe MapUiState.Failure(errorMessage = "Broken map style stream.")
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should increment recenter token when location permission is granted after recenter click`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture()

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.RecenterClicked)
            fixture.viewModel.dispatch(MapIntent.LocationPermissionResult(isGranted = true))
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent { it.recenterRequestToken == 1 }
            actual.recenterRequestToken shouldBe 1
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should expose tracked user location when location tracking starts`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            userLocationUpdates = flowOf(
                UserLocationUpdate.Available(GeoPoint(lat = 40.7128, lon = -74.0060)),
            ),
        )

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.StartLocationTracking)
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent { it.userLocation != null }
            actual.userLocation shouldBe LatLng(latitude = 40.7128, longitude = -74.006)
            actual.cameraPosition?.target shouldBe LatLng(latitude = 40.7128, longitude = -74.006)
            actual.cameraPosition?.zoom shouldBe 15.0
            actual.userLocationTrackingStatus shouldBe UserLocationTrackingStatus.TRACKING
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should start tracking without recenter when location permission is granted before recenter click`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            userLocationUpdates = flowOf(
                UserLocationUpdate.Available(GeoPoint(lat = 51.5074, lon = -0.1278)),
            ),
        )

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.LocationPermissionResult(isGranted = true))
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent { it.userLocation != null }
            actual.recenterRequestToken shouldBe 0
            actual.userLocation shouldBe LatLng(latitude = 51.5074, longitude = -0.1278)
            actual.cameraPosition?.target shouldBe LatLng(latitude = 51.5074, longitude = -0.1278)
            actual.cameraPosition?.zoom shouldBe 15.0
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should emit permission denied tracking status when location permission is unavailable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            userLocationUpdates = flowOf(UserLocationUpdate.PermissionDenied),
        )

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.StartLocationTracking)
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent()
            actual.userLocationTrackingStatus shouldBe UserLocationTrackingStatus.PERMISSION_DENIED
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should reveal exploration tiles when tracked location becomes available`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val location = GeoPoint(lat = 40.7128, lon = -74.0060)
        val fixture = createFixture(
            userLocationUpdates = flowOf(UserLocationUpdate.Available(location)),
            revealTilesResult = setOf(
                ExplorationTile(
                    zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
                    x = 19292,
                    y = 24641,
                ),
            ),
        )

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.StartLocationTracking)
            advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { fixture.revealExplorationTilesAtLocation.invoke(location) }
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should stop collecting user location updates when tracking is stopped`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        var collectionCancelled = false
        val fixture = createFixture(
            userLocationUpdates = flow {
                emit(UserLocationUpdate.TemporarilyUnavailable)
                kotlinx.coroutines.awaitCancellation()
            }.onCompletion {
                collectionCancelled = true
            },
        )

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.StartLocationTracking)
            advanceUntilIdle()
            fixture.viewModel.dispatch(MapIntent.StopLocationTracking)
            advanceUntilIdle()

            // Then
            collectionCancelled shouldBe true
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
            val effectDeferred = async { fixture.viewModel.effects.first() }
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.RecenterClicked)
            fixture.viewModel.dispatch(MapIntent.LocationPermissionResult(isGranted = false))
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe MapEffect.ShowMessage(
                message = "Location permission is required to center the map on your position.",
            )
            val actual = fixture.viewModel.awaitContent()
            actual.recenterRequestToken shouldBe 0
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should ignore location permission result when recenter request was not initiated`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture()

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.LocationPermissionResult(isGranted = false))
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent()
            actual.recenterRequestToken shouldBe 0
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should emit message when current location is unavailable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture()
        val effectDeferred = async { fixture.viewModel.effects.first() }

        try {
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.CurrentLocationUnavailable)
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe MapEffect.ShowMessage(
                message = "Current location is not available yet.",
            )
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should recenter camera and open object details when tapped object is present`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
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
    fun `dispatch should emit error message when object discovery fails`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            discoverError = IllegalStateException("Object discovery failed."),
        )

        try {
            fixture.viewModel.awaitContent()
            val effectDeferred = async { fixture.viewModel.effects.first() }
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.ObjectTapped(objectId = "poi-1"))
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe MapEffect.ShowMessage(message = "Object discovery failed.")
            coVerify(exactly = 1) { fixture.discoverPointOfInterest.invoke("poi-1") }
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should use fallback message when map load failure intent does not include message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture()

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.MapLoadFailed(message = null))
            advanceUntilIdle()

            // Then
            fixture.viewModel.awaitFailure() shouldBe MapUiState.Failure(
                errorMessage = "Failed to load map style.",
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
            actual.fogOfWar.fogRanges.any { fogRange ->
                fogRange.minX < visibleRange.minX ||
                    fogRange.maxX > visibleRange.maxX ||
                    fogRange.minY < visibleRange.minY ||
                    fogRange.maxY > visibleRange.maxY
            } shouldBe true
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should buffer the fog query by one extra screen in every direction while keeping explored stats scoped to the live viewport`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            minX = 10,
            maxX = 13,
            minY = 20,
            maxY = 25,
        )
        val observedFogRanges = mutableListOf<ExplorationTileRange>()
        val fixture = createFixture(
            exploredTiles = setOf(
                ExplorationTile(
                    zoom = visibleRange.zoom,
                    x = 10,
                    y = 20,
                ),
            ),
            observedTileRanges = observedFogRanges,
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
            observedFogRanges.last() shouldBe visibleRange.expandedBy(
                horizontalTilePadding = 4,
                verticalTilePadding = 6,
            )

            val actual = fixture.viewModel.awaitContent { it.fogOfWar.visibleTileCount == 24L }
            actual.fogOfWar.visibleTileCount shouldBe 24L
            actual.fogOfWar.exploredVisibleTileCount shouldBe 1
            actual.fogOfWar.fogRanges.any { fogRange ->
                fogRange.minX < visibleRange.minX ||
                    fogRange.maxX > visibleRange.maxX ||
                    fogRange.minY < visibleRange.minY ||
                    fogRange.maxY > visibleRange.maxY
            } shouldBe true
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should update fog overlay immediately when viewport changes without waiting for camera settled`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            minX = 12,
            maxX = 13,
            minY = 22,
            maxY = 23,
        )
        val fixture = createFixture(
            exploredTiles = setOf(
                ExplorationTile(
                    zoom = visibleRange.zoom,
                    x = 12,
                    y = 22,
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
            actual.cameraPosition shouldBe null
            actual.fogOfWar.visibleTileCount shouldBe 4L
            actual.fogOfWar.exploredVisibleTileCount shouldBe 1
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should not re-subscribe to explored tiles when viewport changes stay within the same canonical tile range`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            minX = 30,
            maxX = 35,
            minY = 40,
            maxY = 43,
        )
        val observedFogRanges = mutableListOf<ExplorationTileRange>()
        val initialBounds = visibleBoundsInside(visibleRange)
        val shiftedBounds = initialBounds.copy(
            south = initialBounds.south + VIEWPORT_BOUNDS_EPSILON,
            west = initialBounds.west + VIEWPORT_BOUNDS_EPSILON,
            north = initialBounds.north - VIEWPORT_BOUNDS_EPSILON,
            east = initialBounds.east - VIEWPORT_BOUNDS_EPSILON,
        )
        val fixture = createFixture(
            observedTileRanges = observedFogRanges,
        )

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = initialBounds,
                ),
            )
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = shiftedBounds,
                ),
            )
            advanceUntilIdle()

            // Then
            observedFogRanges shouldBe listOf(
                visibleRange.expandedBy(
                    horizontalTilePadding = 6,
                    verticalTilePadding = 4,
                )
            )
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should suppress fog when the viewport exceeds the fog safety limit`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            minX = 100,
            maxX = 221,
            minY = 200,
            maxY = 267,
        )
        val observedFogRanges = mutableListOf<ExplorationTileRange>()
        val fixture = createFixture(
            observedTileRanges = observedFogRanges,
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
            val actual = fixture.viewModel.awaitContent {
                it.fogOfWar.visibleTileCount == 8_296L
            }
            actual.fogOfWar.visibleTileCount shouldBe 8_296L
            actual.fogOfWar.fogRanges shouldBe emptyList()
            actual.fogOfWar.isSuppressedByVisibleTileLimit shouldBe true
            observedFogRanges shouldBe emptyList()
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should keep programmatic camera state until camera settles and then update to the settled user position`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            minX = 15,
            maxX = 16,
            minY = 25,
            maxY = 26,
        )
        val fixture = createFixture(
            pointsOfInterest = listOf(
                pointOfInterest(id = "poi-1", lat = 51.1, lon = 17.03),
            ),
        )

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.ObjectTapped(objectId = "poi-1"))
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = visibleBoundsInside(visibleRange),
                ),
            )
            advanceUntilIdle()

            // Then
            val programmatic = fixture.viewModel.awaitContent {
                it.cameraPosition?.target == LatLng(latitude = 51.1, longitude = 17.03)
                    && it.fogOfWar.visibleTileCount == 4L
            }
            programmatic.cameraPosition?.target shouldBe LatLng(latitude = 51.1, longitude = 17.03)
            programmatic.cameraUpdateOrigin shouldBe CameraUpdateOrigin.PROGRAMMATIC

            // When
            fixture.viewModel.dispatch(
                MapIntent.CameraSettled(
                    position = com.github.arhor.journey.feature.map.model.CameraPositionState(
                        target = LatLng(latitude = 51.2, longitude = 17.04),
                        zoom = 14.0,
                    ),
                    origin = CameraUpdateOrigin.USER,
                ),
            )
            advanceUntilIdle()

            // Then
            val settled = fixture.viewModel.awaitContent {
                it.cameraPosition?.target == LatLng(latitude = 51.2, longitude = 17.04)
            }
            settled.cameraPosition?.target shouldBe LatLng(latitude = 51.2, longitude = 17.04)
            settled.cameraPosition?.zoom shouldBe 14.0
            settled.cameraUpdateOrigin shouldBe CameraUpdateOrigin.USER
            settled.fogOfWar.visibleTileCount shouldBe 4L
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
            fixture.viewModel.dispatch(MapIntent.ClearExploredTilesClicked)
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
        val discoverPointOfInterest: DiscoverPointOfInterestUseCase,
        val revealExplorationTilesAtLocation: RevealExplorationTilesAtLocationUseCase,
        val clearExploredTiles: ClearExploredTilesUseCase,
    )

    private fun createFixture(
        mapStyleOutput: Output<MapStyle?, DomainError>? = null,
        pointsOfInterest: List<PointOfInterest> = listOf(
            pointOfInterest(id = "poi-1", lat = 50.45, lon = 30.52),
        ),
        explorationProgress: ExplorationProgress = ExplorationProgress(discovered = emptySet()),
        exploredTiles: Set<ExplorationTile> = emptySet(),
        observedTileRanges: MutableList<ExplorationTileRange>? = null,
        discoverError: Throwable? = null,
        revealTilesResult: Set<ExplorationTile> = emptySet(),
        userLocationUpdates: kotlinx.coroutines.flow.Flow<UserLocationUpdate> =
            flowOf(UserLocationUpdate.TemporarilyUnavailable),
    ): Fixture {
        val observePointsOfInterest = mockk<ObservePointsOfInterestUseCase>()
        val observeExplorationProgress = mockk<ObserveExplorationProgressUseCase>()
        val observeExploredTiles = mockk<ObserveExploredTilesUseCase>()
        val observeSelectedMapStyle = mockk<ObserveSelectedMapStyleUseCase>()
        val discoverPointOfInterest = mockk<DiscoverPointOfInterestUseCase>()
        val revealExplorationTilesAtLocation = mockk<RevealExplorationTilesAtLocationUseCase>()
        val clearExploredTiles = mockk<ClearExploredTilesUseCase>()
        val foregroundUserLocationTracker = mockk<ForegroundUserLocationTracker>()

        val mapStyle = MapStyle.remote(
            id = "style-remote",
            name = "Remote",
            value = "https://example.com/style.json",
        )

        every { observePointsOfInterest.invoke() } returns MutableStateFlow(pointsOfInterest)
        every { observeExplorationProgress.invoke() } returns MutableStateFlow(explorationProgress)
        if (observedTileRanges != null) {
            every { observeExploredTiles.invoke(any()) } answers {
                observedTileRanges += invocation.args.first() as ExplorationTileRange
                MutableStateFlow(exploredTiles)
            }
        } else {
            every { observeExploredTiles.invoke(any()) } returns MutableStateFlow(exploredTiles)
        }
        every { observeSelectedMapStyle.invoke() } returns MutableStateFlow(
            mapStyleOutput ?: Output.Success(mapStyle),
        )

        if (discoverError != null) {
            coEvery { discoverPointOfInterest.invoke(any()) } throws discoverError
        } else {
            coEvery { discoverPointOfInterest.invoke(any()) } just runs
        }

        coEvery { revealExplorationTilesAtLocation.invoke(any()) } returns revealTilesResult
        coEvery { clearExploredTiles.invoke() } just runs

        every { foregroundUserLocationTracker.observeLocations() } returns userLocationUpdates

        return Fixture(
            viewModel = MapViewModel(
                observePointsOfInterest = observePointsOfInterest,
                observeExplorationProgress = observeExplorationProgress,
                observeExploredTiles = observeExploredTiles,
                observeSelectedMapStyle = observeSelectedMapStyle,
                discoverPointOfInterest = discoverPointOfInterest,
                revealExplorationTilesAtLocation = revealExplorationTilesAtLocation,
                clearExploredTiles = clearExploredTiles,
                foregroundUserLocationTracker = foregroundUserLocationTracker,
            ),
            mapStyle = mapStyle,
            discoverPointOfInterest = discoverPointOfInterest,
            revealExplorationTilesAtLocation = revealExplorationTilesAtLocation,
            clearExploredTiles = clearExploredTiles,
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
