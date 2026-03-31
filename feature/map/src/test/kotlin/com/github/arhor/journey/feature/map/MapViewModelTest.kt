package com.github.arhor.journey.feature.map

import androidx.lifecycle.viewModelScope
import com.github.arhor.journey.core.common.DomainError
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.CANONICAL_ZOOM
import com.github.arhor.journey.domain.internal.bounds
import com.github.arhor.journey.domain.model.DiscoveredPoi
import com.github.arhor.journey.domain.model.ExplorationProgress
import com.github.arhor.journey.domain.model.MapTile
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
import com.github.arhor.journey.domain.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.usecase.GetExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.GetExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveCollectibleResourceSpawnsUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObserveExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.StartExplorationTrackingSessionUseCase
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
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
import org.junit.AfterClass
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
                tearDownMainDispatcher(fixture.viewModel)
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
                    resourceTypeId = "scrap",
                    lat = 50.46,
                    lon = 30.53,
                    collectionRadiusMeters = 24.0,
                ),
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
            actual.visibleObjects.map { it.id }.toSet() shouldBe setOf(
                "${POI_ID_PREFIX}:$FIRST_POI_ID",
                "${RESOURCE_SPAWN_ID_PREFIX}:cell-1-slot-0",
            )
            actual.visibleObjects
                .first { it.id == "${RESOURCE_SPAWN_ID_PREFIX}:cell-1-slot-0" }
                .title shouldBe "Scrap"
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should mark only resource spawns outside current visibility mask as hidden by fog`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
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
        val visibleSpawnId = "cell-1-slot-0"
        val hiddenSpawnId = "cell-1-slot-1"
        val fixture = createFixture(
            pointsOfInterest = emptyList(),
            resourceSpawns = listOf(
                resourceSpawn(
                    id = visibleSpawnId,
                    resourceTypeId = "scrap",
                    lat = centerPointOf(visibleTile).lat,
                    lon = centerPointOf(visibleTile).lon,
                    collectionRadiusMeters = 24.0,
                ),
                resourceSpawn(
                    id = hiddenSpawnId,
                    resourceTypeId = "components",
                    lat = centerPointOf(hiddenTile).lat,
                    lon = centerPointOf(hiddenTile).lon,
                    collectionRadiusMeters = 24.0,
                ),
            ),
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = centerPointOf(visibleTile),
            ),
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
                content.visibleObjects.count { it.id.startsWith("$RESOURCE_SPAWN_ID_PREFIX:") } == 2
            }

            // Then
            actual.visibleObjects
                .filter { it.id.startsWith("$RESOURCE_SPAWN_ID_PREFIX:") }
                .associate { it.id to it.isHiddenByFog } shouldBe mapOf(
                "${RESOURCE_SPAWN_ID_PREFIX}:$visibleSpawnId" to false,
                "${RESOURCE_SPAWN_ID_PREFIX}:$hiddenSpawnId" to true,
            )
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should flip resource fog placeholders when tracked visibility moves between spawn tiles`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 500_000,
            maxX = 500_012,
            minY = 500_000,
            maxY = 500_000,
        )
        val firstTile = MapTile(
            zoom = visibleRange.zoom,
            x = visibleRange.minX,
            y = visibleRange.minY,
        )
        val secondTile = MapTile(
            zoom = visibleRange.zoom,
            x = visibleRange.maxX,
            y = visibleRange.minY,
        )
        val firstSpawnId = "cell-2-slot-0"
        val secondSpawnId = "cell-2-slot-1"
        val initialSession = ExplorationTrackingSession(
            isActive = true,
            status = ExplorationTrackingStatus.TRACKING,
            lastKnownLocation = centerPointOf(firstTile),
        )
        val fixture = createFixture(
            pointsOfInterest = emptyList(),
            resourceSpawns = listOf(
                resourceSpawn(
                    id = firstSpawnId,
                    resourceTypeId = "scrap",
                    lat = centerPointOf(firstTile).lat,
                    lon = centerPointOf(firstTile).lon,
                    collectionRadiusMeters = 24.0,
                ),
                resourceSpawn(
                    id = secondSpawnId,
                    resourceTypeId = "fuel",
                    lat = centerPointOf(secondTile).lat,
                    lon = centerPointOf(secondTile).lon,
                    collectionRadiusMeters = 24.0,
                ),
            ),
            trackingSession = initialSession,
        )

        try {
            fixture.viewModel.awaitContent()
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = visibleBoundsInside(visibleRange),
                ),
            )
            advanceUntilIdle()

            val initial = fixture.viewModel.awaitContent { content ->
                content.visibleObjects
                    .filter { it.id.startsWith("$RESOURCE_SPAWN_ID_PREFIX:") }
                    .associate { it.id to it.isHiddenByFog } == mapOf(
                    "${RESOURCE_SPAWN_ID_PREFIX}:$firstSpawnId" to false,
                    "${RESOURCE_SPAWN_ID_PREFIX}:$secondSpawnId" to true,
                )
            }

            // When
            fixture.trackingSessionFlow.value = initialSession.copy(
                lastKnownLocation = centerPointOf(secondTile),
            )
            advanceUntilIdle()

            // Then
            val updated = fixture.viewModel.awaitContent { content ->
                content.visibleObjects
                    .filter { it.id.startsWith("$RESOURCE_SPAWN_ID_PREFIX:") }
                    .associate { it.id to it.isHiddenByFog } == mapOf(
                    "${RESOURCE_SPAWN_ID_PREFIX}:$firstSpawnId" to true,
                    "${RESOURCE_SPAWN_ID_PREFIX}:$secondSpawnId" to false,
                )
            }
            initial.visibleObjects
                .filter { it.id.startsWith("$RESOURCE_SPAWN_ID_PREFIX:") }
                .associate { it.id to it.isHiddenByFog } shouldBe mapOf(
                "${RESOURCE_SPAWN_ID_PREFIX}:$firstSpawnId" to false,
                "${RESOURCE_SPAWN_ID_PREFIX}:$secondSpawnId" to true,
            )
            updated.visibleObjects
                .filter { it.id.startsWith("$RESOURCE_SPAWN_ID_PREFIX:") }
                .associate { it.id to it.isHiddenByFog } shouldBe mapOf(
                "${RESOURCE_SPAWN_ID_PREFIX}:$firstSpawnId" to true,
                "${RESOURCE_SPAWN_ID_PREFIX}:$secondSpawnId" to false,
            )
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should keep resource spawns hidden when they remain outside the tracked visibility mask`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 500_000,
            maxX = 500_012,
            minY = 500_000,
            maxY = 500_000,
        )
        val hiddenTile = MapTile(
            zoom = visibleRange.zoom,
            x = visibleRange.maxX,
            y = visibleRange.minY,
        )
        val revealTile = MapTile(
            zoom = visibleRange.zoom,
            x = visibleRange.minX,
            y = visibleRange.minY,
        )
        val hiddenSpawnId = "cell-3-slot-0"
        val fixture = createFixture(
            pointsOfInterest = emptyList(),
            resourceSpawns = listOf(
                resourceSpawn(
                    id = hiddenSpawnId,
                    resourceTypeId = "components",
                    lat = centerPointOf(hiddenTile).lat,
                    lon = centerPointOf(hiddenTile).lon,
                    collectionRadiusMeters = 24.0,
                ),
            ),
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = centerPointOf(revealTile),
            ),
        )

        try {
            fixture.viewModel.awaitContent()
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = visibleBoundsInside(visibleRange),
                ),
            )
            advanceUntilIdle()

            val initial = fixture.viewModel.awaitContent { content ->
                content.visibleObjects.firstOrNull()?.isHiddenByFog == true
            }

            // Then
            initial.visibleObjects.single().isHiddenByFog shouldBe true
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
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
                    resourceTypeId = "scrap",
                    lat = 50.46,
                    lon = 30.53,
                    collectionRadiusMeters = 24.0,
                ),
            ),
        )
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
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
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should expose runtime-config fog defaults by default`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture()

        try {
            // When
            val actual = fixture.viewModel.awaitContent()

            // Then
            actual.fogOfWar.canonicalZoom shouldBe CANONICAL_ZOOM
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should expose configured canonical zoom when runtime config overrides default`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            tileRuntimeConfig = ExplorationTileRuntimeConfig(
                canonicalZoom = 18,
                revealRadiusMeters = 42.0,
            ),
        )

        try {
            // When
            val actual = fixture.viewModel.awaitContent { it.fogOfWar.canonicalZoom == 18 }

            // Then
            actual.fogOfWar.canonicalZoom shouldBe 18
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
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
            tearDownMainDispatcher(fixture.viewModel)
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
            tearDownMainDispatcher(fixture.viewModel)
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
            tearDownMainDispatcher(fixture.viewModel)
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
            tearDownMainDispatcher(fixture.viewModel)
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
            tearDownMainDispatcher(fixture.viewModel)
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
            tearDownMainDispatcher(fixture.viewModel)
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
                tearDownMainDispatcher(fixture.viewModel)
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
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should derive fog overlay stats when the viewport changes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val fixture = createFixture(
            exploredTiles = setOf(
                MapTile(
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
            val actual = fixture.viewModel.awaitContent { it.fogOfWar.hiddenExploredRenderData != null }
            actual.fogOfWar.visibleTileCount shouldBe 4L
            actual.fogOfWar.visibleExploredTileCount shouldBe 0
            actual.fogOfWar.hiddenExploredRenderData.shouldNotBeNull()
            actual.fogOfWar.activeRenderData.shouldNotBeNull()
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should dim explored tiles when no usable current location is available`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val fixture = createFixture(
            exploredTiles = setOf(
                MapTile(
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
            val actual = fixture.viewModel.awaitContent { it.fogOfWar.hiddenExploredRenderData != null }
            actual.fogOfWar.visibleExploredTileCount shouldBe 0
            actual.fogOfWar.hiddenExploredRenderData.shouldNotBeNull()
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should keep only explored tiles near the tracked location fully visible`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
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
            exploredTiles = setOf(visibleTile, hiddenTile),
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = centerPointOf(visibleTile),
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
            val actual = fixture.viewModel.awaitContent {
                it.fogOfWar.visibleExploredTileCount == 1 &&
                    it.fogOfWar.hiddenExploredRenderData != null
            }
            actual.fogOfWar.visibleExploredTileCount shouldBe 1
            actual.fogOfWar.hiddenExploredRenderData.shouldNotBeNull()
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should avoid full fog recomputation when tracked location jitter stays inside the same visibility mask`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))

            // Given
            val visibleRange = ExplorationTileRange(
                zoom = CANONICAL_ZOOM,
                minX = 10,
                maxX = 11,
                minY = 20,
                maxY = 21,
            )
            val initialLocation = centerPointOf(
                MapTile(
                    zoom = visibleRange.zoom,
                    x = visibleRange.minX,
                    y = visibleRange.minY,
                ),
            )
            val fixture = createFixture(
                observeExploredTilesFlowFactory = { MutableStateFlow(emptySet()) },
                trackingSession = ExplorationTrackingSession(
                    isActive = true,
                    status = ExplorationTrackingStatus.TRACKING,
                    lastKnownLocation = initialLocation,
                ),
            )

            try {
                fixture.viewModel.awaitContent()
                fixture.viewModel.dispatch(
                    MapIntent.CameraViewportChanged(
                        visibleBounds = visibleBoundsInside(visibleRange),
                    ),
                )
                advanceUntilIdle()
                verify(exactly = 1) { fixture.observeExploredTiles.invoke(any()) }

                // When
                fixture.trackingSessionFlow.value = fixture.trackingSessionFlow.value.copy(
                    lastKnownLocation = GeoPoint(
                        lat = initialLocation.lat + 1e-7,
                        lon = initialLocation.lon + 1e-7,
                    ),
                )
                advanceUntilIdle()

                // Then
                verify(exactly = 1) { fixture.observeExploredTiles.invoke(any()) }
            } finally {
                tearDownMainDispatcher(fixture.viewModel)
            }
        }

    @Test
    fun `dispatch should update hidden explored overlay when tracked visibility moves to a different tile cluster`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))

            // Given
            val visibleRange = ExplorationTileRange(
                zoom = CANONICAL_ZOOM,
                minX = 500_000,
                maxX = 500_012,
                minY = 500_000,
                maxY = 500_000,
            )
            val firstTile = MapTile(
                zoom = visibleRange.zoom,
                x = visibleRange.minX,
                y = visibleRange.minY,
            )
            val secondTile = MapTile(
                zoom = visibleRange.zoom,
                x = visibleRange.maxX,
                y = visibleRange.minY,
            )
            val initialSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = centerPointOf(firstTile),
            )
            val fixture = createFixture(
                exploredTiles = setOf(firstTile, secondTile),
                trackingSession = initialSession,
            )

            try {
                fixture.viewModel.awaitContent()
                fixture.viewModel.dispatch(
                    MapIntent.CameraViewportChanged(
                        visibleBounds = visibleBoundsInside(visibleRange),
                    ),
                )
                advanceUntilIdle()
                verify(exactly = 1) { fixture.observeExploredTiles.invoke(any()) }

                val initial = fixture.viewModel.awaitContent {
                    it.fogOfWar.visibleExploredTileCount == 1 &&
                        it.fogOfWar.hiddenExploredRenderData != null
                }
                val initialHiddenExploredRenderData = initial.fogOfWar.hiddenExploredRenderData

                // When
                fixture.trackingSessionFlow.value = initialSession.copy(
                    lastKnownLocation = centerPointOf(secondTile),
                )
                advanceUntilIdle()

                // Then
                val updated = fixture.viewModel.awaitContent {
                    it.fogOfWar.hiddenExploredRenderData != initialHiddenExploredRenderData
                }
                updated.fogOfWar.visibleExploredTileCount shouldBe 1
                verify(exactly = 1) { fixture.observeExploredTiles.invoke(any()) }
            } finally {
                tearDownMainDispatcher(fixture.viewModel)
            }
        }

    @Test
    fun `dispatch should avoid fog recomputation while the viewport stays inside the trigger bounds`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
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
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should swap to the newest fog buffer when the viewport outruns trigger bounds`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val initialVisibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val outrunVisibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 16,
            maxX = 17,
            minY = 20,
            maxY = 21,
        )
        val fixture = createFixture(
            observeExploredTilesFlowFactory = { range ->
                when (range) {
                    expectedFogBufferRange(initialVisibleRange) -> MutableStateFlow(emptySet())
                    expectedFogBufferRange(outrunVisibleRange) -> MutableStateFlow(emptySet())

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
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent { it.fogOfWar.visibleTileRange == outrunVisibleRange }
            actual.fogOfWar.activeRenderData.shouldNotBeNull()
            actual.fogOfWar.bufferedBounds.shouldNotBeNull()
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should keep the newest fog buffer during repeated fast pans`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))

            // Given
            val initialVisibleRange = ExplorationTileRange(
                zoom = CANONICAL_ZOOM,
                minX = 10,
                maxX = 11,
                minY = 20,
                maxY = 21,
            )
            val secondVisibleRange = ExplorationTileRange(
                zoom = CANONICAL_ZOOM,
                minX = 16,
                maxX = 17,
                minY = 20,
                maxY = 21,
            )
            val thirdVisibleRange = ExplorationTileRange(
                zoom = CANONICAL_ZOOM,
                minX = 22,
                maxX = 23,
                minY = 20,
                maxY = 21,
            )
            val fixture = createFixture(
                observeExploredTilesFlowFactory = { range ->
                    when (range) {
                        expectedFogBufferRange(initialVisibleRange) -> MutableStateFlow(emptySet())
                        expectedFogBufferRange(secondVisibleRange),
                        expectedFogBufferRange(thirdVisibleRange) -> MutableStateFlow(emptySet())

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
                advanceUntilIdle()

                // Then
                val actual = fixture.viewModel.awaitContent {
                    it.fogOfWar.visibleTileRange == thirdVisibleRange
                }
                actual.fogOfWar.activeRenderData.shouldNotBeNull()
                actual.fogOfWar.bufferedBounds.shouldNotBeNull()
            } finally {
                tearDownMainDispatcher(fixture.viewModel)
            }
        }

    @Test
    fun `dispatch should keep fog coverage while a newer fog buffer is still recomputing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val initialVisibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val outrunVisibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 18,
            maxX = 19,
            minY = 20,
            maxY = 21,
        )
        val allowPendingSwap = CompletableDeferred<Unit>()
        val fixture = createFixture(
            observeExploredTilesFlowFactory = { MutableStateFlow(emptySet()) },
            getExploredTilesOverride = { range ->
                if (range == expectedFogBufferRange(outrunVisibleRange)) {
                    allowPendingSwap.await()
                }
                emptySet()
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
            val recomputing = fixture.viewModel.awaitContent {
                it.fogOfWar.visibleTileRange == outrunVisibleRange &&
                    it.fogOfWar.isRecomputing
            }
            val hasCoverage = recomputing.fogOfWar.activeRenderData != null ||
                recomputing.fogOfWar.handoffRenderData != null
            hasCoverage shouldBe true
            recomputing.fogOfWar.hiddenExploredRenderData shouldBe null

            allowPendingSwap.complete(Unit)
            runCurrent()
            advanceUntilIdle()
            fixture.viewModel.awaitContent {
                it.fogOfWar.visibleTileRange == outrunVisibleRange &&
                    !it.fogOfWar.isRecomputing
            }
        } finally {
            allowPendingSwap.complete(Unit)
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should recompute exact fog after a pending buffer swap when explored tiles change`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val initialVisibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val shiftedVisibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 14,
            maxX = 15,
            minY = 20,
            maxY = 21,
        )
        val shiftedTile = MapTile(
            zoom = shiftedVisibleRange.zoom,
            x = shiftedVisibleRange.minX,
            y = shiftedVisibleRange.minY,
        )
        val shiftedFlow = MutableStateFlow(emptySet<MapTile>())
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
                it.fogOfWar.visibleTileRange == shiftedVisibleRange
            }
            actual.fogOfWar.activeRenderData.shouldNotBeNull()
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should reuse buffered resource query when the viewport changes inside the current resource bounds`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))

            // Given
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
                tearDownMainDispatcher(fixture.viewModel)
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
            tearDownMainDispatcher(fixture.viewModel)
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
            tearDownMainDispatcher(fixture.viewModel)
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
            tearDownMainDispatcher(fixture.viewModel)
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
                    resourceTypeId = "components",
                    lat = 51.2,
                    lon = 17.1,
                    collectionRadiusMeters = 20.0,
                ),
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
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    private suspend fun MapViewModel.awaitContent(
        predicate: (MapUiState.Content) -> Boolean = { true },
    ): MapUiState.Content = uiState
        .mapNotNull { it as? MapUiState.Content }
        .first(predicate)

    private suspend fun MapViewModel.awaitFailure(): MapUiState.Failure =
        uiState.first { it is MapUiState.Failure } as MapUiState.Failure

    private fun TestScope.tearDownMainDispatcher(viewModel: MapViewModel) {
        viewModel.viewModelScope.cancel()
        advanceTimeBy(5_000L)
        runCurrent()
        advanceUntilIdle()
    }

    private data class Fixture(
        val viewModel: MapViewModel,
        val mapStyle: MapStyle,
        val trackingSessionFlow: MutableStateFlow<ExplorationTrackingSession>,
        val mapTilePrewarmer: MapTilePrewarmer,
        val observeCollectibleResourceSpawns: ObserveCollectibleResourceSpawnsUseCase,
        val observeExploredTiles: ObserveExploredTilesUseCase,
        val discoverPointOfInterest: DiscoverPointOfInterestUseCase,
        val getExploredTiles: GetExploredTilesUseCase,
        val startTrackingSession: StartExplorationTrackingSessionUseCase,
    )

    private fun createFixture(
        mapStyleOutput: Output<MapStyle?, DomainError>? = null,
        pointsOfInterest: List<PointOfInterest> = listOf(
            pointOfInterest(id = FIRST_POI_ID, lat = 50.45, lon = 30.52),
        ),
        resourceSpawns: List<ResourceSpawn> = emptyList(),
        explorationProgress: ExplorationProgress = ExplorationProgress(discovered = emptySet()),
        exploredTiles: Set<MapTile> = emptySet(),
        observeExploredTilesFlowFactory: ((ExplorationTileRange) -> Flow<Set<MapTile>>)? = null,
        getExploredTilesOverride: (suspend (ExplorationTileRange) -> Set<MapTile>)? = null,
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
        val getExploredTiles = mockk<GetExploredTilesUseCase>()
        val getExplorationTileRuntimeConfig = mockk<GetExplorationTileRuntimeConfigUseCase>()
        val observeExplorationTileRuntimeConfig = mockk<ObserveExplorationTileRuntimeConfigUseCase>()
        val observeExplorationTrackingSession = mockk<ObserveExplorationTrackingSessionUseCase>()
        val startTrackingSession = mockk<StartExplorationTrackingSessionUseCase>()
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
        every { mapTilePrewarmer.prewarm(any()) } returns Job()
        coEvery { getExploredTiles.invoke(any()) } coAnswers {
            getExploredTilesOverride?.invoke(arg(0))
                ?: observeExploredTilesFlowFactory?.invoke(arg(0))?.first()
                ?: exploredTiles
        }

        coEvery { discoverPointOfInterest.invoke(any()) } just runs
        coEvery { startTrackingSession.invoke() } returns startTrackingResult

        return Fixture(
            viewModel = MapViewModel(
                observePointsOfInterest = observePointsOfInterest,
                observeCollectibleResourceSpawns = observeCollectibleResourceSpawns,
                observeExplorationProgress = observeExplorationProgress,
                observeSelectedMapStyle = observeSelectedMapStyle,
                discoverPointOfInterest = discoverPointOfInterest,
                getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfig,
                fogOfWarControllerFactory = { scope ->
                    FogOfWarController(
                        observeExplorationTileRuntimeConfig = observeExplorationTileRuntimeConfig,
                        observeExplorationTrackingSession = observeExplorationTrackingSession,
                        observeExploredTiles = observeExploredTiles,
                        getExploredTiles = getExploredTiles,
                        renderDataFactory = FowRenderDataFactory(),
                        fogOfWarCalculator = FogOfWarCalculator(),
                        scope = scope,
                    )
                },
                observeExplorationTrackingSession = observeExplorationTrackingSession,
                startExplorationTrackingSession = startTrackingSession,
                mapTilePrewarmer = mapTilePrewarmer,
            ),
            mapStyle = mapStyle,
            trackingSessionFlow = trackingSessionFlow,
            mapTilePrewarmer = mapTilePrewarmer,
            observeCollectibleResourceSpawns = observeCollectibleResourceSpawns,
            observeExploredTiles = observeExploredTiles,
            discoverPointOfInterest = discoverPointOfInterest,
            getExploredTiles = getExploredTiles,
            startTrackingSession = startTrackingSession,
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
        bounds(range).let { bounds ->
            GeoBounds(
                south = bounds.south + VIEWPORT_BOUNDS_EPSILON,
                west = bounds.west + VIEWPORT_BOUNDS_EPSILON,
                north = bounds.north - VIEWPORT_BOUNDS_EPSILON,
                east = bounds.east - VIEWPORT_BOUNDS_EPSILON,
            )
        }

    private fun centerPointOf(tile: MapTile): GeoPoint =
        bounds(tile).let { tileBounds ->
            GeoPoint(
                lat = (tileBounds.south + tileBounds.north) / 2.0,
                lon = (tileBounds.west + tileBounds.east) / 2.0,
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
            triggerHorizontalPadding + 2,
            ceil(widthInTiles * bufferedMultiplier).toInt().coerceAtLeast(1),
        )
        val bufferedVerticalPadding = maxOf(
            triggerVerticalPadding + 2,
            ceil(heightInTiles * bufferedMultiplier).toInt().coerceAtLeast(1),
        )

        return range.expandedBy(
            horizontalTilePadding = bufferedHorizontalPadding,
            verticalTilePadding = bufferedVerticalPadding,
        )
    }

    private data class TestDomainError(
        override val message: String? = null,
        override val cause: Throwable? = null,
    ) : DomainError

    private companion object {
        @JvmStatic
        @AfterClass
        fun resetMainDispatcherAfterClass() {
            Dispatchers.resetMain()
        }

        const val VIEWPORT_BOUNDS_EPSILON = 1e-6
        const val FIRST_POI_ID = 1L
        const val SECOND_POI_ID = 2L
        const val POI_ID_PREFIX = "poi"
        const val RESOURCE_SPAWN_ID_PREFIX = "spawn"
    }
}
