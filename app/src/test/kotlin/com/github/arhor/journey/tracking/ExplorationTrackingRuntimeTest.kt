package com.github.arhor.journey.tracking

import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfigHolder
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import com.github.arhor.journey.testing.MainDispatcherRule
import com.github.arhor.journey.tracking.location.UserLocationSource
import com.github.arhor.journey.tracking.location.UserLocationUpdate
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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
        val explorationTileRepository = mockk<ExplorationTileRepository>()
        val runtime = ExplorationTrackingRuntime(
            appScope = backgroundScope,
            userLocationSource = userLocationSource,
            explorationTileRepository = explorationTileRepository,
            configHolder = ExplorationTileRuntimeConfigHolder(),
        )
        val location = GeoPoint(lat = 40.7128, lon = -74.0060)
        coEvery { explorationTileRepository.accumulateExplorationTileLights(any()) } just runs

        // When
        runtime.startIfNeeded()
        runCurrent()
        userLocationSource.emit(UserLocationUpdate.Available(location))
        runCurrent()
        userLocationSource.emit(UserLocationUpdate.Available(location))
        runCurrent()

        // Then
        coVerify(exactly = 1) { explorationTileRepository.accumulateExplorationTileLights(any()) }
        runtime.snapshot().status shouldBe ExplorationTrackingStatus.TRACKING
        runtime.snapshot().lastKnownLocation shouldBe location
    }

    @Test
    fun `handleLocationUpdate should expose disabled state when providers are unavailable`() = runTest {
        // Given
        val userLocationSource = FakeUserLocationSource()
        val runtime = ExplorationTrackingRuntime(
            appScope = backgroundScope,
            userLocationSource = userLocationSource,
            explorationTileRepository = mockk(relaxed = true),
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
    }

    @Test
    fun `stop should move session inactive and preserve the most recent cadence`() = runTest {
        // Given
        val runtime = ExplorationTrackingRuntime(
            appScope = backgroundScope,
            userLocationSource = FakeUserLocationSource(),
            explorationTileRepository = mockk(relaxed = true),
            configHolder = ExplorationTileRuntimeConfigHolder(),
        )

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
        val explorationTileRepository = mockk<ExplorationTileRepository>()
        val runtime = ExplorationTrackingRuntime(
            appScope = backgroundScope,
            userLocationSource = userLocationSource,
            explorationTileRepository = explorationTileRepository,
            configHolder = configHolder,
        )
        val location = GeoPoint(lat = 40.7128, lon = -74.0060)
        coEvery { explorationTileRepository.accumulateExplorationTileLights(any()) } just runs

        // When
        runtime.startIfNeeded()
        runCurrent()
        userLocationSource.emit(UserLocationUpdate.Available(location))
        runCurrent()
        configHolder.setCanonicalZoom(18)
        userLocationSource.emit(UserLocationUpdate.Available(location))
        runCurrent()

        // Then
        coVerify(exactly = 2) { explorationTileRepository.accumulateExplorationTileLights(any()) }
    }

    private class FakeUserLocationSource : UserLocationSource {
        private val updates = MutableSharedFlow<UserLocationUpdate>(replay = 1, extraBufferCapacity = 8)

        override fun observeLocations(cadence: Flow<ExplorationTrackingCadence>): Flow<UserLocationUpdate> = updates

        suspend fun emit(update: UserLocationUpdate) {
            updates.emit(update)
        }
    }
}
