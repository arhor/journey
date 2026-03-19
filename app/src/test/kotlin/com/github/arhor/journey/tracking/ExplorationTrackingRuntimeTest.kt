package com.github.arhor.journey.tracking

import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfigHolder
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.usecase.CollectNearbyResourceSpawnsUseCase
import com.github.arhor.journey.domain.usecase.RevealExplorationTilesAtLocationUseCase
import com.github.arhor.journey.testing.MainDispatcherRule
import com.github.arhor.journey.tracking.location.UserLocationSource
import com.github.arhor.journey.tracking.location.UserLocationUpdate
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ExplorationTrackingRuntimeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `startIfNeeded should reveal exploration tiles once for repeated updates in the same tile cluster`() = runTest {
        // Given
        val userLocationSource = FakeUserLocationSource()
        val revealExplorationTilesAtLocation = mockk<RevealExplorationTilesAtLocationUseCase>()
        val collectNearbyResourceSpawns = mockk<CollectNearbyResourceSpawnsUseCase>()
        val runtime = ExplorationTrackingRuntime(
            appScope = backgroundScope,
            userLocationSource = userLocationSource,
            revealExplorationTilesAtLocation = revealExplorationTilesAtLocation,
            collectNearbyResourceSpawns = collectNearbyResourceSpawns,
            configHolder = ExplorationTileRuntimeConfigHolder(),
        )
        val location = GeoPoint(lat = 40.7128, lon = -74.0060)
        coEvery { revealExplorationTilesAtLocation.invoke(any()) } returns emptySet()
        coEvery { collectNearbyResourceSpawns.invoke(any()) } returns emptyList()

        // When
        runtime.startIfNeeded()
        runCurrent()
        userLocationSource.emit(UserLocationUpdate.Available(location))
        runCurrent()
        userLocationSource.emit(UserLocationUpdate.Available(location))
        runCurrent()

        // Then
        coVerify(exactly = 1) { revealExplorationTilesAtLocation.invoke(location) }
        coVerify(exactly = 1) { collectNearbyResourceSpawns.invoke(location) }
        runtime.snapshot().status shouldBe ExplorationTrackingStatus.TRACKING
        runtime.snapshot().lastKnownLocation shouldBe location
    }

    @Test
    fun `handleLocationUpdate should expose disabled state when providers are unavailable`() = runTest {
        // Given
        val userLocationSource = FakeUserLocationSource()
        val collectNearbyResourceSpawns = mockk<CollectNearbyResourceSpawnsUseCase>(relaxed = true)
        val runtime = ExplorationTrackingRuntime(
            appScope = backgroundScope,
            userLocationSource = userLocationSource,
            revealExplorationTilesAtLocation = mockk(relaxed = true),
            collectNearbyResourceSpawns = collectNearbyResourceSpawns,
            configHolder = ExplorationTileRuntimeConfigHolder(),
        )

        // When
        runtime.startIfNeeded()
        runCurrent()
        userLocationSource.emit(UserLocationUpdate.LocationServicesDisabled)
        runCurrent()

        // Then
        runtime.snapshot().isActive shouldBe true
        runtime.snapshot().status shouldBe ExplorationTrackingStatus.LOCATION_SERVICES_DISABLED
        coVerify(exactly = 0) { collectNearbyResourceSpawns.invoke(any()) }
    }

    @Test
    fun `stop should move session inactive and preserve the most recent cadence`() = runTest {
        // Given
        val collectNearbyResourceSpawns = mockk<CollectNearbyResourceSpawnsUseCase>()
        val runtime = ExplorationTrackingRuntime(
            appScope = backgroundScope,
            userLocationSource = FakeUserLocationSource(),
            revealExplorationTilesAtLocation = mockk(relaxed = true),
            collectNearbyResourceSpawns = collectNearbyResourceSpawns,
            configHolder = ExplorationTileRuntimeConfigHolder(),
        )
        coEvery { collectNearbyResourceSpawns.invoke(any()) } returns emptyList()

        // When
        runtime.setCadence(ExplorationTrackingCadence.FOREGROUND)
        runtime.startIfNeeded()
        advanceUntilIdle()
        runtime.stop()
        advanceUntilIdle()

        // Then
        runtime.snapshot().isActive shouldBe false
        runtime.snapshot().status shouldBe ExplorationTrackingStatus.INACTIVE
        runtime.snapshot().cadence shouldBe ExplorationTrackingCadence.FOREGROUND
    }

    @Test
    fun `startIfNeeded should reveal again for the same location after canonical zoom changes`() = runTest {
        // Given
        val userLocationSource = FakeUserLocationSource()
        val configHolder = ExplorationTileRuntimeConfigHolder()
        val revealExplorationTilesAtLocation = mockk<RevealExplorationTilesAtLocationUseCase>()
        val collectNearbyResourceSpawns = mockk<CollectNearbyResourceSpawnsUseCase>()
        val runtime = ExplorationTrackingRuntime(
            appScope = backgroundScope,
            userLocationSource = userLocationSource,
            revealExplorationTilesAtLocation = revealExplorationTilesAtLocation,
            collectNearbyResourceSpawns = collectNearbyResourceSpawns,
            configHolder = configHolder,
        )
        val location = GeoPoint(lat = 40.7128, lon = -74.0060)
        coEvery { revealExplorationTilesAtLocation.invoke(any()) } returns emptySet()
        coEvery { collectNearbyResourceSpawns.invoke(any()) } returns emptyList()

        // When
        runtime.startIfNeeded()
        runCurrent()
        userLocationSource.emit(UserLocationUpdate.Available(location))
        runCurrent()
        configHolder.setCanonicalZoom(18)
        userLocationSource.emit(UserLocationUpdate.Available(location))
        runCurrent()

        // Then
        coVerify(exactly = 2) { revealExplorationTilesAtLocation.invoke(location) }
        coVerify(exactly = 1) { collectNearbyResourceSpawns.invoke(location) }
    }

    @Test
    fun `startIfNeeded should trigger nearby collection for distinct location buckets even when reveal tiles are unchanged`() = runTest {
        // Given
        val userLocationSource = FakeUserLocationSource()
        val configHolder = ExplorationTileRuntimeConfigHolder().apply {
            setCanonicalZoom(1)
        }
        val revealExplorationTilesAtLocation = mockk<RevealExplorationTilesAtLocationUseCase>()
        val collectNearbyResourceSpawns = mockk<CollectNearbyResourceSpawnsUseCase>()
        val runtime = ExplorationTrackingRuntime(
            appScope = backgroundScope,
            userLocationSource = userLocationSource,
            revealExplorationTilesAtLocation = revealExplorationTilesAtLocation,
            collectNearbyResourceSpawns = collectNearbyResourceSpawns,
            configHolder = configHolder,
        )
        val firstLocation = GeoPoint(lat = 40.7128, lon = -74.0060)
        val secondLocation = GeoPoint(lat = 40.7134, lon = -74.0066)
        coEvery { revealExplorationTilesAtLocation.invoke(any()) } returns emptySet()
        coEvery { collectNearbyResourceSpawns.invoke(any()) } returns emptyList()

        // When
        runtime.startIfNeeded()
        runCurrent()
        userLocationSource.emit(UserLocationUpdate.Available(firstLocation))
        runCurrent()
        userLocationSource.emit(UserLocationUpdate.Available(secondLocation))
        runCurrent()

        // Then
        coVerify(exactly = 1) { revealExplorationTilesAtLocation.invoke(firstLocation) }
        coVerify(exactly = 2) {
            collectNearbyResourceSpawns.invoke(any())
        }
        coVerify(exactly = 1) { collectNearbyResourceSpawns.invoke(firstLocation) }
        coVerify(exactly = 1) { collectNearbyResourceSpawns.invoke(secondLocation) }
    }

    @Test
    fun `stop should reset nearby collection guard for the next session`() = runTest {
        // Given
        val userLocationSource = FakeUserLocationSource()
        val collectNearbyResourceSpawns = mockk<CollectNearbyResourceSpawnsUseCase>()
        val runtime = ExplorationTrackingRuntime(
            appScope = backgroundScope,
            userLocationSource = userLocationSource,
            revealExplorationTilesAtLocation = mockk(relaxed = true),
            collectNearbyResourceSpawns = collectNearbyResourceSpawns,
            configHolder = ExplorationTileRuntimeConfigHolder(),
        )
        val location = GeoPoint(lat = 40.7128, lon = -74.0060)
        coEvery { collectNearbyResourceSpawns.invoke(any()) } returns emptyList()

        // When
        runtime.startIfNeeded()
        runCurrent()
        userLocationSource.emit(UserLocationUpdate.Available(location))
        runCurrent()
        runtime.stop()
        runCurrent()
        runtime.startIfNeeded()
        runCurrent()
        userLocationSource.emit(UserLocationUpdate.Available(location))
        runCurrent()

        // Then
        coVerify(exactly = 2) { collectNearbyResourceSpawns.invoke(location) }
    }

    private class FakeUserLocationSource : UserLocationSource {
        private val updates = MutableSharedFlow<UserLocationUpdate>(replay = 1, extraBufferCapacity = 8)

        override fun observeLocations(cadence: Flow<ExplorationTrackingCadence>): Flow<UserLocationUpdate> = updates

        suspend fun emit(update: UserLocationUpdate) {
            updates.emit(update)
        }
    }
}
