package com.github.arhor.journey.tracking

import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.StartExplorationTrackingSessionResult
import com.github.arhor.journey.tracking.location.LocationPermissionChecker
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ExplorationTrackingSessionControllerImplTest {

    @Test
    fun `startSessionIfNeeded should return permission required when location permission is missing`() = runTest {
        // Given
        val runtime = mockk<ExplorationTrackingRuntime>()
        val permissionChecker = mockk<LocationPermissionChecker>()
        val launcher = mockk<ExplorationTrackingServiceLauncher>()
        val controller = ExplorationTrackingSessionControllerImpl(runtime, permissionChecker, launcher)

        every { runtime.snapshot() } returns ExplorationTrackingSession()
        every { permissionChecker.hasAnyLocationPermission() } returns false
        every { runtime.markPermissionDenied() } just runs

        // When
        val actual = controller.startSessionIfNeeded()

        // Then
        actual shouldBe StartExplorationTrackingSessionResult.PermissionRequired
        verify(exactly = 1) { runtime.markPermissionDenied() }
        verify(exactly = 0) { launcher.start() }
    }

    @Test
    fun `startSessionIfNeeded should be idempotent when session is already active`() = runTest {
        // Given
        val runtime = mockk<ExplorationTrackingRuntime>()
        val controller = ExplorationTrackingSessionControllerImpl(
            runtime = runtime,
            permissionChecker = mockk(),
            serviceLauncher = mockk(),
        )

        every {
            runtime.snapshot()
        } returns ExplorationTrackingSession(isActive = true)

        // When
        val actual = controller.startSessionIfNeeded()

        // Then
        actual shouldBe StartExplorationTrackingSessionResult.AlreadyActive
    }

    @Test
    fun `startSessionIfNeeded should mark session starting and launch the foreground service when permission is available`() = runTest {
        // Given
        val runtime = mockk<ExplorationTrackingRuntime>()
        val permissionChecker = mockk<LocationPermissionChecker>()
        val launcher = mockk<ExplorationTrackingServiceLauncher>()
        val controller = ExplorationTrackingSessionControllerImpl(runtime, permissionChecker, launcher)

        every { runtime.snapshot() } returns ExplorationTrackingSession()
        every { permissionChecker.hasAnyLocationPermission() } returns true
        every { runtime.markStarting() } just runs
        every { launcher.start() } just runs

        // When
        val actual = controller.startSessionIfNeeded()

        // Then
        actual shouldBe StartExplorationTrackingSessionResult.Started
        verify(exactly = 1) { runtime.markStarting() }
        verify(exactly = 1) { launcher.start() }
    }

    @Test
    fun `stopSession should stop runtime and service`() = runTest {
        // Given
        val runtime = mockk<ExplorationTrackingRuntime>()
        val launcher = mockk<ExplorationTrackingServiceLauncher>()
        val controller = ExplorationTrackingSessionControllerImpl(
            runtime = runtime,
            permissionChecker = mockk(),
            serviceLauncher = launcher,
        )

        every { runtime.stop() } just runs
        every { launcher.stop() } just runs

        // When
        controller.stopSession()

        // Then
        verify(exactly = 1) { runtime.stop() }
        verify(exactly = 1) { launcher.stop() }
    }

    @Test
    fun `setCadence should update runtime cadence`() = runTest {
        // Given
        val runtime = mockk<ExplorationTrackingRuntime>()
        val controller = ExplorationTrackingSessionControllerImpl(
            runtime = runtime,
            permissionChecker = mockk(),
            serviceLauncher = mockk(),
        )

        every { runtime.setCadence(any()) } just runs

        // When
        controller.setCadence(ExplorationTrackingCadence.BACKGROUND)

        // Then
        verify(exactly = 1) { runtime.setCadence(ExplorationTrackingCadence.BACKGROUND) }
    }
}
