package com.github.arhor.journey.feature.map

import androidx.lifecycle.viewModelScope
import com.github.arhor.journey.core.common.DomainError
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.ResourceType
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
import com.github.arhor.journey.domain.model.Watchtower
import com.github.arhor.journey.domain.model.WatchtowerPhase
import com.github.arhor.journey.domain.model.WatchtowerResourceCost
import com.github.arhor.journey.domain.model.WatchtowerRevealSnapshot
import com.github.arhor.journey.domain.model.WatchtowerState
import com.github.arhor.journey.domain.model.error.ClaimWatchtowerError
import com.github.arhor.journey.domain.model.error.StartExplorationTrackingSessionError
import com.github.arhor.journey.domain.model.error.UpgradeWatchtowerError
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ClaimWatchtowerUseCase
import com.github.arhor.journey.domain.usecase.GetExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.GetWatchtowerUseCase
import com.github.arhor.journey.domain.usecase.GetExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveClaimedWatchtowerRevealTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveCollectibleResourceSpawnsUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObserveExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveHeroResourceAmountUseCase
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.ObserveVisibleWatchtowersUseCase
import com.github.arhor.journey.domain.usecase.StartExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.UpgradeWatchtowerUseCase
import com.github.arhor.journey.feature.map.fow.FogOfWarCalculator
import com.github.arhor.journey.feature.map.fow.FogOfWarController
import com.github.arhor.journey.feature.map.fow.FowRenderDataFactory
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapViewportSize
import com.github.arhor.journey.feature.map.model.WatchtowerMarkerState
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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
    fun `dispatch should expose a local watchtower sheet when a watchtower marker is tapped`() = runTest {
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
            pointsOfInterest = emptyList(),
            watchtowers = listOf(
                watchtower(
                    id = "tower-1",
                    location = centerPointOf(
                        MapTile(
                            zoom = visibleRange.zoom,
                            x = visibleRange.minX,
                            y = visibleRange.minY,
                        ),
                    ),
                    phase = WatchtowerPhase.DISCOVERED_DORMANT,
                    canClaim = true,
                    claimCost = WatchtowerResourceCost(
                        resourceTypeId = ResourceType.SCRAP.typeId,
                        amount = 5,
                    ),
                ),
            ),
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = centerPointOf(
                    MapTile(
                        zoom = visibleRange.zoom,
                        x = visibleRange.minX,
                        y = visibleRange.minY,
                    ),
                ),
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
            fixture.viewModel.dispatch(
                MapIntent.ObjectTapped("$WATCHTOWER_ID_PREFIX:tower-1"),
            )
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent { it.selectedWatchtower?.id == "tower-1" }
            actual.visibleObjects.any { it.id == "$WATCHTOWER_ID_PREFIX:tower-1" } shouldBe true
            actual.selectedWatchtower?.canClaim shouldBe true
            actual.selectedWatchtower?.claimCostLabel shouldBe "5 Scrap"
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should keep the selected watchtower sheet while camera moves outside its visible marker bounds`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val initialRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val offscreenRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 100,
            maxX = 101,
            minY = 200,
            maxY = 201,
        )
        val tower = watchtower(
            id = "tower-persistent",
            location = centerPointOf(
                MapTile(
                    zoom = initialRange.zoom,
                    x = initialRange.minX,
                    y = initialRange.minY,
                ),
            ),
            phase = WatchtowerPhase.DISCOVERED_DORMANT,
            claimCost = WatchtowerResourceCost(
                resourceTypeId = ResourceType.SCRAP.typeId,
                amount = 5,
            ),
        )
        val fixture = createFixture(
            pointsOfInterest = emptyList(),
            watchtowers = listOf(tower),
            observeVisibleWatchtowersFlowFactory = { bounds ->
                MutableStateFlow(
                    if (bounds.contains(tower.location)) {
                        listOf(tower)
                    } else {
                        emptyList()
                    },
                )
            },
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = tower.location,
            ),
        )

        try {
            fixture.viewModel.awaitContent()
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = visibleBoundsInside(initialRange),
                ),
            )
            advanceUntilIdle()
            fixture.viewModel.dispatch(MapIntent.ObjectTapped("$WATCHTOWER_ID_PREFIX:tower-persistent"))
            advanceUntilIdle()

            // When
            fixture.viewModel.dispatch(
                MapIntent.CameraViewportChanged(
                    visibleBounds = visibleBoundsInside(offscreenRange),
                ),
            )
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent { it.selectedWatchtower?.id == "tower-persistent" }
            actual.selectedWatchtower?.id shouldBe "tower-persistent"
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should not mark a watchtower claimable when the player is in range but lacks resources`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val towerLocation = centerPointOf(
            MapTile(
                zoom = visibleRange.zoom,
                x = visibleRange.minX,
                y = visibleRange.minY,
            ),
        )
        val fixture = createFixture(
            pointsOfInterest = emptyList(),
            watchtowers = listOf(
                watchtower(
                    id = "tower-unaffordable",
                    location = towerLocation,
                    phase = WatchtowerPhase.DISCOVERED_DORMANT,
                    claimCost = WatchtowerResourceCost(
                        resourceTypeId = ResourceType.SCRAP.typeId,
                        amount = 5,
                    ),
                ),
            ),
            resourceAmountsByType = mapOf(
                ResourceType.SCRAP.typeId to 0,
                ResourceType.COMPONENTS.typeId to 99,
                ResourceType.FUEL.typeId to 99,
            ),
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = towerLocation,
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
            fixture.viewModel.dispatch(MapIntent.ObjectTapped("$WATCHTOWER_ID_PREFIX:tower-unaffordable"))
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent { it.selectedWatchtower?.id == "tower-unaffordable" }
            actual.visibleObjects
                .first { it.id == "$WATCHTOWER_ID_PREFIX:tower-unaffordable" }
                .watchtowerMarkerState shouldBe WatchtowerMarkerState.DISCOVERED_DORMANT
            actual.selectedWatchtower?.canClaim shouldBe false
            actual.selectedWatchtower?.claimDisabledReason shouldBe "Not enough Scrap."
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should show claimed message when selected watchtower claim succeeds`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val towerLocation = centerPointOf(
            MapTile(
                zoom = visibleRange.zoom,
                x = visibleRange.minX,
                y = visibleRange.minY,
            ),
        )
        val fixture = createFixture(
            pointsOfInterest = emptyList(),
            watchtowers = listOf(
                watchtower(
                    id = "tower-1",
                    location = towerLocation,
                    phase = WatchtowerPhase.DISCOVERED_DORMANT,
                    canClaim = true,
                    claimCost = WatchtowerResourceCost(
                        resourceTypeId = ResourceType.SCRAP.typeId,
                        amount = 5,
                    ),
                ),
            ),
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = towerLocation,
            ),
            claimWatchtowerResult = Output.Success(
                watchtowerState(
                    watchtowerId = "tower-1",
                    claimedAt = Instant.parse("2026-04-01T10:00:00Z"),
                    level = 1,
                    updatedAt = Instant.parse("2026-04-01T10:00:00Z"),
                ),
            ),
        )

        try {
            selectWatchtower(
                fixture = fixture,
                visibleRange = visibleRange,
                watchtowerId = "tower-1",
            )
            val effectDeferred = async { fixture.viewModel.effects.first() }
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.ClaimSelectedWatchtower)
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe MapEffect.ShowMessage("Watchtower claimed.")
            coVerify(exactly = 1) { fixture.claimWatchtower.invoke("tower-1", towerLocation) }
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should clear selected watchtower when claim result reports not found`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val towerLocation = centerPointOf(
            MapTile(
                zoom = visibleRange.zoom,
                x = visibleRange.minX,
                y = visibleRange.minY,
            ),
        )
        val fixture = createFixture(
            pointsOfInterest = emptyList(),
            watchtowers = listOf(
                watchtower(
                    id = "tower-1",
                    location = towerLocation,
                    phase = WatchtowerPhase.DISCOVERED_DORMANT,
                    canClaim = true,
                    claimCost = WatchtowerResourceCost(
                        resourceTypeId = ResourceType.SCRAP.typeId,
                        amount = 5,
                    ),
                ),
            ),
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = towerLocation,
            ),
            claimWatchtowerResult = Output.Failure(
                ClaimWatchtowerError.NotFound("tower-1"),
            ),
        )

        try {
            selectWatchtower(
                fixture = fixture,
                visibleRange = visibleRange,
                watchtowerId = "tower-1",
            )
            val effectDeferred = async { fixture.viewModel.effects.first() }
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.ClaimSelectedWatchtower)
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe MapEffect.ShowMessage("Watchtower is no longer available.")
            val actual = fixture.viewModel.awaitContent { it.selectedWatchtower == null }
            actual.selectedWatchtower shouldBe null
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should show resource requirement message when claim result reports insufficient resources`() =
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
            val towerLocation = centerPointOf(
                MapTile(
                    zoom = visibleRange.zoom,
                    x = visibleRange.minX,
                    y = visibleRange.minY,
                ),
            )
            val fixture = createFixture(
                pointsOfInterest = emptyList(),
                watchtowers = listOf(
                    watchtower(
                        id = "tower-1",
                        location = towerLocation,
                        phase = WatchtowerPhase.DISCOVERED_DORMANT,
                        canClaim = true,
                        claimCost = WatchtowerResourceCost(
                            resourceTypeId = ResourceType.SCRAP.typeId,
                            amount = 5,
                        ),
                    ),
                ),
                trackingSession = ExplorationTrackingSession(
                    isActive = true,
                    status = ExplorationTrackingStatus.TRACKING,
                    lastKnownLocation = towerLocation,
                ),
                claimWatchtowerResult = Output.Failure(
                    ClaimWatchtowerError.InsufficientResources(
                        watchtowerId = "tower-1",
                        resourceTypeId = ResourceType.SCRAP.typeId,
                        requiredAmount = 5,
                        availableAmount = 0,
                    ),
                ),
            )

            try {
                selectWatchtower(
                    fixture = fixture,
                    visibleRange = visibleRange,
                    watchtowerId = "tower-1",
                )
                val effectDeferred = async { fixture.viewModel.effects.first() }
                runCurrent()

                // When
                fixture.viewModel.dispatch(MapIntent.ClaimSelectedWatchtower)
                advanceUntilIdle()

                // Then
                effectDeferred.await() shouldBe MapEffect.ShowMessage("Not enough Scrap.")
            } finally {
                tearDownMainDispatcher(fixture.viewModel)
            }
        }

    @Test
    fun `dispatch should show upgraded level when selected watchtower upgrade succeeds`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val towerLocation = centerPointOf(
            MapTile(
                zoom = visibleRange.zoom,
                x = visibleRange.minX,
                y = visibleRange.minY,
            ),
        )
        val fixture = createFixture(
            pointsOfInterest = emptyList(),
            watchtowers = listOf(
                watchtower(
                    id = "tower-1",
                    location = towerLocation,
                    phase = WatchtowerPhase.CLAIMED,
                    canUpgrade = true,
                    level = 1,
                    nextUpgradeCost = WatchtowerResourceCost(
                        resourceTypeId = ResourceType.COMPONENTS.typeId,
                        amount = 10,
                    ),
                ),
            ),
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = towerLocation,
            ),
            upgradeWatchtowerResult = Output.Success(
                watchtowerState(
                    watchtowerId = "tower-1",
                    claimedAt = Instant.parse("2026-03-31T10:00:00Z"),
                    level = 7,
                    updatedAt = Instant.parse("2026-04-01T10:00:00Z"),
                ),
            ),
        )

        try {
            selectWatchtower(
                fixture = fixture,
                visibleRange = visibleRange,
                watchtowerId = "tower-1",
            )
            val effectDeferred = async { fixture.viewModel.effects.first() }
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.UpgradeSelectedWatchtower)
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe MapEffect.ShowMessage("Watchtower upgraded to level 7")
            coVerify(exactly = 1) { fixture.upgradeWatchtower.invoke("tower-1", towerLocation) }
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should show max level message when upgrade result reports max level`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val towerLocation = centerPointOf(
            MapTile(
                zoom = visibleRange.zoom,
                x = visibleRange.minX,
                y = visibleRange.minY,
            ),
        )
        val fixture = createFixture(
            pointsOfInterest = emptyList(),
            watchtowers = listOf(
                watchtower(
                    id = "tower-1",
                    location = towerLocation,
                    phase = WatchtowerPhase.CLAIMED,
                    canUpgrade = true,
                    level = 1,
                    nextUpgradeCost = WatchtowerResourceCost(
                        resourceTypeId = ResourceType.COMPONENTS.typeId,
                        amount = 10,
                    ),
                ),
            ),
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = towerLocation,
            ),
            upgradeWatchtowerResult = Output.Failure(
                UpgradeWatchtowerError.AlreadyAtMaxLevel("tower-1"),
            ),
        )

        try {
            selectWatchtower(
                fixture = fixture,
                visibleRange = visibleRange,
                watchtowerId = "tower-1",
            )
            val effectDeferred = async { fixture.viewModel.effects.first() }
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.UpgradeSelectedWatchtower)
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe MapEffect.ShowMessage("Watchtower is already at maximum level.")
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should show not claimed message when upgrade result reports unclaimed watchtower`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val towerLocation = centerPointOf(
            MapTile(
                zoom = visibleRange.zoom,
                x = visibleRange.minX,
                y = visibleRange.minY,
            ),
        )
        val fixture = createFixture(
            pointsOfInterest = emptyList(),
            watchtowers = listOf(
                watchtower(
                    id = "tower-1",
                    location = towerLocation,
                    phase = WatchtowerPhase.CLAIMED,
                    canUpgrade = true,
                    level = 1,
                    nextUpgradeCost = WatchtowerResourceCost(
                        resourceTypeId = ResourceType.COMPONENTS.typeId,
                        amount = 10,
                    ),
                ),
            ),
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = towerLocation,
            ),
            upgradeWatchtowerResult = Output.Failure(
                UpgradeWatchtowerError.NotClaimed("tower-1"),
            ),
        )

        try {
            selectWatchtower(
                fixture = fixture,
                visibleRange = visibleRange,
                watchtowerId = "tower-1",
            )
            val effectDeferred = async { fixture.viewModel.effects.first() }
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.UpgradeSelectedWatchtower)
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe MapEffect.ShowMessage("Claim the watchtower before upgrading it.")
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should clear selected watchtower when upgrade result reports not found`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val towerLocation = centerPointOf(
            MapTile(
                zoom = visibleRange.zoom,
                x = visibleRange.minX,
                y = visibleRange.minY,
            ),
        )
        val fixture = createFixture(
            pointsOfInterest = emptyList(),
            watchtowers = listOf(
                watchtower(
                    id = "tower-1",
                    location = towerLocation,
                    phase = WatchtowerPhase.CLAIMED,
                    canUpgrade = true,
                    level = 1,
                    nextUpgradeCost = WatchtowerResourceCost(
                        resourceTypeId = ResourceType.COMPONENTS.typeId,
                        amount = 10,
                    ),
                ),
            ),
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = towerLocation,
            ),
            upgradeWatchtowerResult = Output.Failure(
                UpgradeWatchtowerError.NotFound("tower-1"),
            ),
        )

        try {
            selectWatchtower(
                fixture = fixture,
                visibleRange = visibleRange,
                watchtowerId = "tower-1",
            )
            val effectDeferred = async { fixture.viewModel.effects.first() }
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.UpgradeSelectedWatchtower)
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe MapEffect.ShowMessage("Watchtower is no longer available.")
            val actual = fixture.viewModel.awaitContent { it.selectedWatchtower == null }
            actual.selectedWatchtower shouldBe null
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should show out of range message when upgrade result reports distance failure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val towerLocation = centerPointOf(
            MapTile(
                zoom = visibleRange.zoom,
                x = visibleRange.minX,
                y = visibleRange.minY,
            ),
        )
        val fixture = createFixture(
            pointsOfInterest = emptyList(),
            watchtowers = listOf(
                watchtower(
                    id = "tower-1",
                    location = towerLocation,
                    phase = WatchtowerPhase.CLAIMED,
                    canUpgrade = true,
                    level = 1,
                    nextUpgradeCost = WatchtowerResourceCost(
                        resourceTypeId = ResourceType.COMPONENTS.typeId,
                        amount = 10,
                    ),
                ),
            ),
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = towerLocation,
            ),
            upgradeWatchtowerResult = Output.Failure(
                UpgradeWatchtowerError.NotInRange(
                    watchtowerId = "tower-1",
                    distanceMeters = 50.0,
                    interactionRadiusMeters = 25.0,
                ),
            ),
        )

        try {
            selectWatchtower(
                fixture = fixture,
                visibleRange = visibleRange,
                watchtowerId = "tower-1",
            )
            val effectDeferred = async { fixture.viewModel.effects.first() }
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.UpgradeSelectedWatchtower)
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe MapEffect.ShowMessage("Move closer to interact with the watchtower.")
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should show resource requirement message when upgrade result reports insufficient resources`() =
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
            val towerLocation = centerPointOf(
                MapTile(
                    zoom = visibleRange.zoom,
                    x = visibleRange.minX,
                    y = visibleRange.minY,
                ),
            )
            val fixture = createFixture(
                pointsOfInterest = emptyList(),
                watchtowers = listOf(
                    watchtower(
                        id = "tower-1",
                        location = towerLocation,
                        phase = WatchtowerPhase.CLAIMED,
                        canUpgrade = true,
                        level = 1,
                        nextUpgradeCost = WatchtowerResourceCost(
                            resourceTypeId = ResourceType.COMPONENTS.typeId,
                            amount = 10,
                        ),
                    ),
                ),
                trackingSession = ExplorationTrackingSession(
                    isActive = true,
                    status = ExplorationTrackingStatus.TRACKING,
                    lastKnownLocation = towerLocation,
                ),
                upgradeWatchtowerResult = Output.Failure(
                    UpgradeWatchtowerError.InsufficientResources(
                        watchtowerId = "tower-1",
                        resourceTypeId = ResourceType.COMPONENTS.typeId,
                        requiredAmount = 10,
                        availableAmount = 0,
                    ),
                ),
            )

            try {
                selectWatchtower(
                    fixture = fixture,
                    visibleRange = visibleRange,
                    watchtowerId = "tower-1",
                )
                val effectDeferred = async { fixture.viewModel.effects.first() }
                runCurrent()

                // When
                fixture.viewModel.dispatch(MapIntent.UpgradeSelectedWatchtower)
                advanceUntilIdle()

                // Then
                effectDeferred.await() shouldBe MapEffect.ShowMessage("Not enough Components.")
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
            startTrackingResult = Output.Success(Unit),
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
            startTrackingResult = Output.Failure(StartExplorationTrackingSessionError.PermissionRequired),
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
    fun `dispatch should show fallback message when map open tracking launch fails without message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            startTrackingResult = Output.Failure(
                StartExplorationTrackingSessionError.LaunchFailed(
                    IllegalStateException(),
                ),
            ),
        )

        try {
            fixture.viewModel.awaitContent()
            val effectDeferred = async {
                fixture.viewModel.effects
                    .first { it is MapEffect.ShowMessage }
            }
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.MapOpened)
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe MapEffect.ShowMessage(
                "Failed to start exploration tracking.",
            )
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
    fun `dispatch should keep following updated user location when camera settles from a user gesture`() = runTest {
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
            bearing = 45.0,
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
                it.cameraPosition?.target == LatLng(latitude = 40.7306, longitude = -73.9352)
            }
            actual.cameraPosition shouldBe CameraPositionState(
                target = LatLng(latitude = 40.7306, longitude = -73.9352),
                zoom = 15.0,
                bearing = 45.0,
            )
            actual.cameraUpdateOrigin shouldBe CameraUpdateOrigin.PROGRAMMATIC
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should reset bearing and increment north reset token when location permission is granted after recenter click`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))

            // Given
            val fixture = createFixture(
                trackingSession = ExplorationTrackingSession(
                    lastKnownLocation = GeoPoint(lat = 40.7128, lon = -74.0060),
                ),
                startTrackingResult = Output.Success(Unit),
            )
            val manualCameraPosition = CameraPositionState(
                target = LatLng(latitude = 40.7580, longitude = -73.9855),
                zoom = 15.0,
                bearing = 135.0,
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
                fixture.viewModel.awaitContent {
                    it.cameraPosition == CameraPositionState(
                        target = LatLng(latitude = 40.7306, longitude = -73.9352),
                        zoom = 15.0,
                        bearing = 135.0,
                    )
                }

                // When
                fixture.viewModel.dispatch(MapIntent.RecenterClicked)
                fixture.viewModel.dispatch(MapIntent.LocationPermissionResult(isGranted = true))
                advanceUntilIdle()

                // Then
                val actual = fixture.viewModel.awaitContent {
                    it.northResetRequestToken == 1 &&
                        it.cameraPosition?.target == LatLng(latitude = 40.7306, longitude = -73.9352)
                }
                actual.northResetRequestToken shouldBe 1
                actual.cameraPosition shouldBe CameraPositionState(
                    target = LatLng(latitude = 40.7306, longitude = -73.9352),
                    zoom = 15.0,
                    bearing = 0.0,
                )
                actual.cameraUpdateOrigin shouldBe CameraUpdateOrigin.PROGRAMMATIC
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
                it.cameraPosition?.target == LatLng(latitude = 40.7306, longitude = -73.9352)
            }
            actual.cameraPosition?.target shouldBe LatLng(latitude = 40.7306, longitude = -73.9352)
            actual.cameraUpdateOrigin shouldBe CameraUpdateOrigin.PROGRAMMATIC
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should use tapped anchor for add poi and fall back to user location when missing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val fixture = createFixture(
            trackingSession = ExplorationTrackingSession(
                lastKnownLocation = GeoPoint(lat = 40.7128, lon = -74.0060),
            ),
        )
        val tappedLocation = LatLng(latitude = 40.7580, longitude = -73.9855)

        try {
            fixture.viewModel.awaitContent()
            val firstEffect = async { fixture.viewModel.effects.first() }
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.AddPoiClicked)
            advanceUntilIdle()

            // Then
            firstEffect.await() shouldBe MapEffect.OpenAddPoi(latitude = 40.7128, longitude = -74.006)

            val secondEffect = async { fixture.viewModel.effects.first() }
            runCurrent()

            // When
            fixture.viewModel.dispatch(MapIntent.MapTapped(target = tappedLocation))
            fixture.viewModel.dispatch(MapIntent.AddPoiClicked)
            advanceUntilIdle()

            // Then
            secondEffect.await() shouldBe MapEffect.OpenAddPoi(
                latitude = 40.7580,
                longitude = -73.9855,
            )
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should preserve user location target when gesture starts after programmatic move`() = runTest {
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
            bearing = 30.0,
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
                it.cameraUpdateOrigin == CameraUpdateOrigin.USER
            }
            actual.cameraPosition shouldBe CameraPositionState(
                target = LatLng(latitude = 40.7128, longitude = -74.006),
                zoom = 15.5,
                bearing = 30.0,
            )
            actual.cameraUpdateOrigin shouldBe CameraUpdateOrigin.USER
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should not recenter camera and should open object details when tapped object is present`() = runTest {
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
            val initial = fixture.viewModel.awaitContent()
            val effectDeferred = async { fixture.viewModel.effects.first() }
            runCurrent()

            // When
            fixture.viewModel.dispatch(
                MapIntent.ObjectTapped(
                    objectId = "${POI_ID_PREFIX}:$FIRST_POI_ID",
                ),
            )
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe MapEffect.OpenObjectDetails(objectId = FIRST_POI_ID.toString())
            coVerify(exactly = 1) { fixture.discoverPointOfInterest.invoke(FIRST_POI_ID) }
            fixture.viewModel.awaitContent().cameraPosition shouldBe initial.cameraPosition

            fixture.trackingSessionFlow.value = fixture.trackingSessionFlow.value.copy(
                lastKnownLocation = GeoPoint(lat = 40.7306, lon = -73.9352),
            )
            advanceUntilIdle()
            val actual = fixture.viewModel.awaitContent {
                it.cameraPosition?.target == LatLng(latitude = 40.7306, longitude = -73.9352)
            }
            actual.cameraPosition?.target shouldBe LatLng(latitude = 40.7306, longitude = -73.9352)
            actual.cameraUpdateOrigin shouldBe CameraUpdateOrigin.PROGRAMMATIC
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `dispatch should not recenter camera and should not open POI details when tapped object is a resource spawn`() = runTest {
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
            val initial = fixture.viewModel.awaitContent()
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
            fixture.viewModel.awaitContent().cameraPosition shouldBe initial.cameraPosition
            val actual = fixture.viewModel.awaitContent {
                it.cameraPosition?.target == LatLng(latitude = 40.7128, longitude = -74.006)
            }
            actual.cameraPosition?.target shouldBe LatLng(latitude = 40.7128, longitude = -74.006)
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

    private suspend fun TestScope.selectWatchtower(
        fixture: Fixture,
        visibleRange: ExplorationTileRange,
        watchtowerId: String,
    ) {
        fixture.viewModel.awaitContent()
        fixture.viewModel.dispatch(
            MapIntent.CameraViewportChanged(
                visibleBounds = visibleBoundsInside(visibleRange),
            ),
        )
        advanceUntilIdle()
        fixture.viewModel.dispatch(
            MapIntent.ObjectTapped("$WATCHTOWER_ID_PREFIX:$watchtowerId"),
        )
        advanceUntilIdle()
        fixture.viewModel.awaitContent { it.selectedWatchtower?.id == watchtowerId }
    }

    private data class Fixture(
        val viewModel: MapViewModel,
        val mapStyle: MapStyle,
        val trackingSessionFlow: MutableStateFlow<ExplorationTrackingSession>,
        val observeCollectibleResourceSpawns: ObserveCollectibleResourceSpawnsUseCase,
        val observeExploredTiles: ObserveExploredTilesUseCase,
        val observeVisibleWatchtowers: ObserveVisibleWatchtowersUseCase,
        val discoverPointOfInterest: DiscoverPointOfInterestUseCase,
        val claimWatchtower: ClaimWatchtowerUseCase,
        val upgradeWatchtower: UpgradeWatchtowerUseCase,
        val getWatchtower: GetWatchtowerUseCase,
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
        watchtowerRevealSnapshot: WatchtowerRevealSnapshot = WatchtowerRevealSnapshot(emptySet()),
        watchtowers: List<Watchtower> = emptyList(),
        observeVisibleWatchtowersFlowFactory: ((GeoBounds) -> Flow<List<Watchtower>>)? = null,
        resourceAmountsByType: Map<String, Int> = mapOf(
            ResourceType.SCRAP.typeId to 99,
            ResourceType.COMPONENTS.typeId to 99,
            ResourceType.FUEL.typeId to 99,
        ),
        observeExploredTilesFlowFactory: ((ExplorationTileRange) -> Flow<Set<MapTile>>)? = null,
        getExploredTilesOverride: (suspend (ExplorationTileRange) -> Set<MapTile>)? = null,
        trackingSession: ExplorationTrackingSession = ExplorationTrackingSession(),
        tileRuntimeConfig: ExplorationTileRuntimeConfig = ExplorationTileRuntimeConfig(),
        startTrackingResult: Output<Unit, StartExplorationTrackingSessionError> = Output.Success(Unit),
        claimWatchtowerResult: Output<WatchtowerState, ClaimWatchtowerError> = Output.Success(
            watchtowerState(
                watchtowerId = "watchtower-1",
                claimedAt = Instant.parse("2026-04-01T10:00:00Z"),
                level = 1,
                updatedAt = Instant.parse("2026-04-01T10:00:00Z"),
            ),
        ),
        upgradeWatchtowerResult: Output<WatchtowerState, UpgradeWatchtowerError> = Output.Success(
            watchtowerState(
                watchtowerId = "watchtower-1",
                claimedAt = Instant.parse("2026-03-31T10:00:00Z"),
                level = 2,
                updatedAt = Instant.parse("2026-04-01T10:00:00Z"),
            ),
        ),
    ): Fixture {
        val observePointsOfInterest = mockk<ObservePointsOfInterestUseCase>()
        val observeCollectibleResourceSpawns = mockk<ObserveCollectibleResourceSpawnsUseCase>()
        val observeExplorationProgress = mockk<ObserveExplorationProgressUseCase>()
        val observeExploredTiles = mockk<ObserveExploredTilesUseCase>()
        val observeClaimedWatchtowerRevealTiles = mockk<ObserveClaimedWatchtowerRevealTilesUseCase>()
        val observeVisibleWatchtowers = mockk<ObserveVisibleWatchtowersUseCase>()
        val observeHeroResourceAmount = mockk<ObserveHeroResourceAmountUseCase>()
        val observeSelectedMapStyle = mockk<ObserveSelectedMapStyleUseCase>()
        val discoverPointOfInterest = mockk<DiscoverPointOfInterestUseCase>()
        val claimWatchtower = mockk<ClaimWatchtowerUseCase>()
        val upgradeWatchtower = mockk<UpgradeWatchtowerUseCase>()
        val getWatchtower = mockk<GetWatchtowerUseCase>()
        val getExploredTiles = mockk<GetExploredTilesUseCase>()
        val getExplorationTileRuntimeConfig = mockk<GetExplorationTileRuntimeConfigUseCase>()
        val observeExplorationTileRuntimeConfig = mockk<ObserveExplorationTileRuntimeConfigUseCase>()
        val observeExplorationTrackingSession = mockk<ObserveExplorationTrackingSessionUseCase>()
        val startTrackingSession = mockk<StartExplorationTrackingSessionUseCase>()

        val mapStyle = MapStyle.remote(
            id = "style-remote",
            name = "Remote",
            value = "https://example.com/style.json",
        )
        val trackingSessionFlow = MutableStateFlow(trackingSession)

        every { observePointsOfInterest.invoke() } returns MutableStateFlow(Output.Success(pointsOfInterest))
        every { observeCollectibleResourceSpawns.invoke(any()) } returns MutableStateFlow(Output.Success(resourceSpawns))
        every { observeExplorationProgress.invoke() } returns MutableStateFlow(Output.Success(explorationProgress))
        every { observeExploredTiles.invoke(any()) } answers {
            observeExploredTilesFlowFactory?.invoke(arg(0))?.map { Output.Success(it) }
                ?: MutableStateFlow(Output.Success(exploredTiles))
        }
        every { observeClaimedWatchtowerRevealTiles.invoke(any(), any()) } returns MutableStateFlow(Output.Success(watchtowerRevealSnapshot))
        every { observeVisibleWatchtowers.invoke(any()) } answers {
            observeVisibleWatchtowersFlowFactory?.invoke(arg(0))?.map { Output.Success(it) }
                ?: MutableStateFlow(Output.Success(watchtowers))
        }
        every { observeHeroResourceAmount.invoke(any()) } answers {
            MutableStateFlow(Output.Success(resourceAmountsByType[arg(0)] ?: 0))
        }
        every { observeSelectedMapStyle.invoke() } returns MutableStateFlow(
            mapStyleOutput ?: Output.Success(mapStyle),
        )
        coEvery { getWatchtower.invoke(any()) } answers {
            watchtowers.firstOrNull { it.id == arg(0) }
                ?.let { Output.Success(it) }
                ?: Output.Failure(
                    UseCaseError.NotFound(
                        subject = "Watchtower",
                        identifier = arg<String>(0),
                    ),
                )
        }
        every { getExplorationTileRuntimeConfig.invoke() } returns Output.Success(tileRuntimeConfig)
        every { observeExplorationTileRuntimeConfig.invoke() } returns MutableStateFlow(Output.Success(tileRuntimeConfig))
        every { observeExplorationTrackingSession.invoke() } returns trackingSessionFlow.map { Output.Success(it) }
        coEvery { getExploredTiles.invoke(any()) } coAnswers {
            Output.Success(
                getExploredTilesOverride?.invoke(arg(0))
                    ?: observeExploredTilesFlowFactory?.invoke(arg(0))?.first()
                    ?: exploredTiles,
            )
        }

        coEvery { discoverPointOfInterest.invoke(any()) } returns Output.Success(Unit)
        coEvery { claimWatchtower.invoke(any(), any()) } returns claimWatchtowerResult
        coEvery { upgradeWatchtower.invoke(any(), any()) } returns upgradeWatchtowerResult
        coEvery { startTrackingSession.invoke() } returns startTrackingResult

        return Fixture(
            viewModel = MapViewModel(
                observePointsOfInterest = observePointsOfInterest,
                observeCollectibleResourceSpawns = observeCollectibleResourceSpawns,
                observeExplorationProgress = observeExplorationProgress,
                observeSelectedMapStyle = observeSelectedMapStyle,
                discoverPointOfInterest = discoverPointOfInterest,
                observeVisibleWatchtowers = observeVisibleWatchtowers,
                observeHeroResourceAmount = observeHeroResourceAmount,
                claimWatchtower = claimWatchtower,
                upgradeWatchtower = upgradeWatchtower,
                getWatchtower = getWatchtower,
                getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfig,
                fogOfWarControllerFactory = { scope ->
                    FogOfWarController(
                        observeExplorationTileRuntimeConfig = observeExplorationTileRuntimeConfig,
                        observeExplorationTrackingSession = observeExplorationTrackingSession,
                        observeExploredTiles = observeExploredTiles,
                        observeClaimedWatchtowerRevealTiles = observeClaimedWatchtowerRevealTiles,
                        getExploredTiles = getExploredTiles,
                        renderDataFactory = FowRenderDataFactory(),
                        fogOfWarCalculator = FogOfWarCalculator(),
                        scope = scope,
                    )
                },
                observeExplorationTrackingSession = observeExplorationTrackingSession,
                startExplorationTrackingSession = startTrackingSession,
            ),
            mapStyle = mapStyle,
            trackingSessionFlow = trackingSessionFlow,
            observeCollectibleResourceSpawns = observeCollectibleResourceSpawns,
            observeExploredTiles = observeExploredTiles,
            observeVisibleWatchtowers = observeVisibleWatchtowers,
            discoverPointOfInterest = discoverPointOfInterest,
            claimWatchtower = claimWatchtower,
            upgradeWatchtower = upgradeWatchtower,
            getWatchtower = getWatchtower,
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

    private fun watchtower(
        id: String,
        location: GeoPoint,
        phase: WatchtowerPhase,
        canClaim: Boolean = false,
        canUpgrade: Boolean = false,
        level: Int? = null,
        revealRadiusMeters: Double? = null,
        nextRevealRadiusMeters: Double? = null,
        claimCost: WatchtowerResourceCost? = null,
        nextUpgradeCost: WatchtowerResourceCost? = null,
        distanceMeters: Double? = 0.0,
    ): Watchtower =
        Watchtower(
            id = id,
            name = "Watchtower $id",
            description = "Description $id",
            location = location,
            interactionRadiusMeters = 25.0,
            phase = phase,
            level = level,
            revealRadiusMeters = revealRadiusMeters,
            claimCost = claimCost,
            nextUpgradeCost = nextUpgradeCost,
            nextRevealRadiusMeters = nextRevealRadiusMeters,
            canClaim = canClaim,
            canUpgrade = canUpgrade,
            distanceMeters = distanceMeters,
        )

    private fun watchtowerState(
        watchtowerId: String,
        discoveredAt: Instant = Instant.parse("2026-03-31T08:00:00Z"),
        claimedAt: Instant? = null,
        level: Int = 0,
        updatedAt: Instant = discoveredAt,
    ): WatchtowerState =
        WatchtowerState(
            watchtowerId = watchtowerId,
            discoveredAt = discoveredAt,
            claimedAt = claimedAt,
            level = level,
            updatedAt = updatedAt,
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
        const val WATCHTOWER_ID_PREFIX = "watchtower"
    }
}
