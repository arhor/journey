@file:Suppress("UnusedFlow")

package com.github.arhor.journey.feature.map

import androidx.lifecycle.viewModelScope
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.testing.FakeH3Grid
import com.github.arhor.journey.core.testing.MainDispatcherRule
import com.github.arhor.journey.domain.CANONICAL_ZOOM
import com.github.arhor.journey.domain.internal.bounds
import com.github.arhor.journey.domain.model.BreachNode
import com.github.arhor.journey.domain.model.BreachNodeDefinition
import com.github.arhor.journey.domain.model.BreachNodePhase
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
import com.github.arhor.journey.feature.map.fow.H3FogRevealMapper
import com.github.arhor.journey.feature.map.model.BreachMarkerState
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapObjectKind
import com.github.arhor.journey.feature.map.model.MapObjectUiModel
import com.github.arhor.journey.feature.map.BreachDirectionalGuidanceUiState
import com.github.arhor.journey.feature.map.presentation.BreachNodePresenter
import com.github.arhor.journey.feature.map.presentation.BreachDirectionalGuidancePresenter
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.time.Instant

//import java.time.Instant

class MapViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(mainDispatcher)

    @Test
    fun `uiState should expose idle breach protocol state before pulse`() = runTest(mainDispatcher.scheduler) {
        // Given
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
    fun `uiState should expose signal locked breach protocol state after pulse`() = runTest(mainDispatcher.scheduler) {
        // Given
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
                canStartUpload = true,
                disabledReason = null,
            )
            actual.breachGuidance shouldBe BreachDirectionalGuidanceUiState.OnTarget(
                breachNodeId = breachRecord.definition.id,
                districtName = breachRecord.definition.districtName,
                distanceMeters = 0,
                canStartUpload = true,
            )
            coVerify(exactly = 1) { findNearestBreachNode.invoke(actorLocation) }
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should expose floating breach guidance when locked breach is outside upload radius`() = runTest(mainDispatcher.scheduler) {
        // Given
        val actorLocation = GeoPoint(lat = 0.0, lon = 0.0)
        val breachLocation = GeoPoint(lat = 0.0, lon = 0.01)
        val breachRecord = breachRecord(
            id = "breach-node:v1:h3r9:cell-guidance",
            cellId = "cell-guidance",
            location = breachLocation,
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
                content.breachGuidance is BreachDirectionalGuidanceUiState.FloatingArrow
            }
            actual.breachGuidance shouldBe BreachDirectionalGuidanceUiState.FloatingArrow(
                breachNodeId = breachRecord.definition.id,
                districtName = breachRecord.definition.districtName,
                bearingDegrees = actorLocation.bearingTo(breachLocation),
                distanceMeters = actorLocation.distanceTo(breachLocation).toInt(),
                canStartUpload = false,
            )
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should update signal locked breach protocol when tracking location moves into range`() = runTest(mainDispatcher.scheduler) {
        // Given
        val outOfRangeLocation = GeoPoint(lat = 0.0, lon = 0.0)
        val inRangeLocation = GeoPoint(lat = 0.0, lon = 0.01)
        val breachRecord = breachRecord(
            id = "breach-node:v1:h3r9:cell-live-update",
            cellId = "cell-live-update",
            location = inRangeLocation,
        )
        val findNearestBreachNode = mockk<FindNearestBreachNodeUseCase>()
        coEvery { findNearestBreachNode.invoke(outOfRangeLocation) } returns Output.Success(breachRecord)
        val fixture = createFixture(
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = outOfRangeLocation,
            ),
            findNearestBreachNode = findNearestBreachNode,
        )

        try {
            fixture.viewModel.awaitContent()
            fixture.viewModel.dispatch(MapIntent.PulseClicked)
            advanceUntilIdle()

            val lockedOutOfRange = fixture.viewModel.awaitContent { content ->
                content.breachProtocol is BreachProtocolUiState.SignalLocked &&
                    content.breachGuidance is BreachDirectionalGuidanceUiState.FloatingArrow
            }
            lockedOutOfRange.breachProtocol shouldBe BreachProtocolUiState.SignalLocked(
                breachNodeId = breachRecord.definition.id,
                districtName = breachRecord.definition.districtName,
                distanceMeters = outOfRangeLocation.distanceTo(inRangeLocation).toInt(),
                canStartUpload = false,
                disabledReason = "Move closer to start upload.",
            )

            // When
            fixture.trackingSessionFlow.value = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = inRangeLocation,
            )
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent { content ->
                content.breachGuidance is BreachDirectionalGuidanceUiState.OnTarget
            }
            actual.breachGuidance shouldBe BreachDirectionalGuidanceUiState.OnTarget(
                breachNodeId = breachRecord.definition.id,
                districtName = breachRecord.definition.districtName,
                distanceMeters = 0,
                canStartUpload = true,
            )
            actual.breachProtocol shouldBe BreachProtocolUiState.SignalLocked(
                breachNodeId = breachRecord.definition.id,
                districtName = breachRecord.definition.districtName,
                distanceMeters = 0,
                canStartUpload = true,
                disabledReason = null,
            )

            // And
            fixture.viewModel.dispatch(MapIntent.StartBreachUpload)
            advanceUntilIdle()
            val uploading = fixture.viewModel.awaitContent { content ->
                content.breachProtocol is BreachProtocolUiState.Uploading
            }
            uploading.breachProtocol shouldBe BreachProtocolUiState.Uploading(
                breachNodeId = breachRecord.definition.id,
                districtName = breachRecord.definition.districtName,
                progressPercent = 0,
            )
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should expose unavailable breach guidance when current location is lost during signal locked tracking`() = runTest(mainDispatcher.scheduler) {
        // Given
        val actorLocation = GeoPoint(lat = 0.0, lon = 0.0)
        val breachLocation = GeoPoint(lat = 0.0, lon = 0.01)
        val breachRecord = breachRecord(
            id = "breach-node:v1:h3r9:cell-location-loss",
            cellId = "cell-location-loss",
            location = breachLocation,
        )
        val findNearestBreachNode = mockk<FindNearestBreachNodeUseCase>()
        coEvery { findNearestBreachNode.invoke(actorLocation) } returns Output.Success(breachRecord)
        val fixture = createFixture(
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = actorLocation,
            ),
            findNearestBreachNode = findNearestBreachNode,
        )

        try {
            fixture.viewModel.awaitContent()
            fixture.viewModel.dispatch(MapIntent.PulseClicked)
            advanceUntilIdle()

            val lockedOutOfRange = fixture.viewModel.awaitContent { content ->
                content.breachProtocol is BreachProtocolUiState.SignalLocked &&
                    content.breachGuidance is BreachDirectionalGuidanceUiState.FloatingArrow
            }
            lockedOutOfRange.breachProtocol shouldBe BreachProtocolUiState.SignalLocked(
                breachNodeId = breachRecord.definition.id,
                districtName = breachRecord.definition.districtName,
                distanceMeters = actorLocation.distanceTo(breachLocation).toInt(),
                canStartUpload = false,
                disabledReason = "Move closer to start upload.",
            )

            // When
            fixture.trackingSessionFlow.value = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = null,
            )
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent { content ->
                content.breachGuidance is BreachDirectionalGuidanceUiState.Unavailable
            }
            actual.breachGuidance shouldBe BreachDirectionalGuidanceUiState.Unavailable(
                breachNodeId = breachRecord.definition.id,
                districtName = breachRecord.definition.districtName,
                message = "Location required to continue breach scan.",
            )
            actual.breachProtocol shouldBe BreachProtocolUiState.SignalLocked(
                breachNodeId = breachRecord.definition.id,
                districtName = breachRecord.definition.districtName,
                distanceMeters = actorLocation.distanceTo(breachLocation).toInt(),
                canStartUpload = false,
                disabledReason = "Location required to continue breach scan.",
            )
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should expose on target breach guidance when actor is within interaction radius`() = runTest(mainDispatcher.scheduler) {
        // Given
        val actorLocation = GeoPoint(lat = 50.45, lon = 30.52)
        val breachRecord = breachRecord(
            id = "breach-node:v1:h3r9:cell-on-target",
            cellId = "cell-on-target",
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
                content.breachGuidance is BreachDirectionalGuidanceUiState.OnTarget
            }
            actual.breachGuidance shouldBe BreachDirectionalGuidanceUiState.OnTarget(
                breachNodeId = breachRecord.definition.id,
                districtName = breachRecord.definition.districtName,
                distanceMeters = 0,
                canStartUpload = true,
            )
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should hide breach guidance when breach panel is dismissed`() = runTest(mainDispatcher.scheduler) {
        // Given
        val actorLocation = GeoPoint(lat = 50.45, lon = 30.52)
        val breachRecord = breachRecord(
            id = "breach-node:v1:h3r9:cell-dismiss",
            cellId = "cell-dismiss",
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
            fixture.viewModel.dispatch(MapIntent.PulseClicked)
            advanceUntilIdle()

            // When
            fixture.viewModel.dispatch(MapIntent.DismissBreachPanel)
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent { content ->
                content.breachGuidance is BreachDirectionalGuidanceUiState.Hidden
            }
            actual.breachGuidance shouldBe BreachDirectionalGuidanceUiState.Hidden
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should complete breach upload after repeated ticks`() = runTest(mainDispatcher.scheduler) {
        // Given
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
    @Ignore("flaky")
    fun `uiState should expose selected map style uri`() = runTest(mainDispatcher.scheduler) {

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
    fun `uiState should expose empty visible objects when viewport changes`() = runTest(mainDispatcher.scheduler) {

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

    @Test
    @Ignore("flaky")
    fun `uiState should expose breach visible objects when viewport changes`() = runTest(mainDispatcher.scheduler) {
        // Given
        val breachNode = visibleBreachNode(
            id = "breach-node:v1:h3r9:cell-visible",
            cellId = "cell-visible",
            phase = BreachNodePhase.DISCOVERED,
            canStartUpload = true,
        )
        val observeVisibleBreachNodes = mockk<ObserveVisibleBreachNodesUseCase>()
        val fixture = createFixture(
            observeVisibleBreachNodes = observeVisibleBreachNodes,
        )
        every { observeVisibleBreachNodes.invoke(any()) } returns flowOf(Output.Success(listOf(breachNode)))
        val visibleRange = ExplorationTileRange(
            zoom = CANONICAL_ZOOM,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
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
            val actual = fixture.viewModel.uiState.value as MapUiState.Content
            actual.visibleObjects shouldBe listOf(
                MapObjectUiModel(
                    id = breachNode.definition.id,
                    kind = MapObjectKind.BreachNode,
                    title = breachNode.definition.districtName,
                    description = breachNode.definition.description,
                    position = LatLng(
                        latitude = breachNode.definition.location.lat,
                        longitude = breachNode.definition.location.lon,
                    ),
                    radiusMeters = breachNode.definition.interactionRadiusMeters.toInt(),
                    isDiscovered = true,
                    markerState = BreachMarkerState.UPLOAD_READY,
                ),
            )
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

    @Suppress("SameParameterValue")
    private fun visibleBreachNode(
        id: String,
        cellId: String,
        phase: BreachNodePhase,
        canStartUpload: Boolean,
    ): BreachNode =
        BreachNode(
            definition = BreachNodeDefinition(
                id = id,
                h3CellId = cellId,
                districtName = "district-$cellId",
                description = "Recovered node",
                location = GeoPoint(lat = 50.45, lon = 30.52),
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
            phase = phase,
            distanceMeters = null,
            canDiscover = false,
            canStartUpload = canStartUpload,
        )

    private data class Fixture(
        val viewModel: MapViewModel,
        val trackingSessionFlow: MutableStateFlow<ExplorationTrackingSession>,
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
                        observeControlledBreachRevealCells = observeControlledBreachRevealCells,
                        h3FogRevealMapper = H3FogRevealMapper(FakeH3Grid()),
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
                breachNodePresenter = BreachNodePresenter(),
                breachDirectionalGuidancePresenter = BreachDirectionalGuidancePresenter(),
            ),
            trackingSessionFlow = trackingSessionFlow,
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

    companion object {
        private const val VIEWPORT_BOUNDS_EPSILON = 1e-6
        private val FIXED_INSTANT = Instant.parse("2026-05-15T12:00:00Z")
    }
}
