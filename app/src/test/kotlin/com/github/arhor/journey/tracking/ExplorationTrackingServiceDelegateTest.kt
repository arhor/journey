package com.github.arhor.journey.tracking

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Test

class ExplorationTrackingServiceDelegateTest {

    @Test
    fun `handleCommand should start foreground tracking when start action arrives`() {
        // Given
        val runtime = mockk<ExplorationTrackingRuntime>()
        val delegate = ExplorationTrackingServiceDelegate(runtime)
        var startedForeground = false

        every { runtime.startIfNeeded() } just runs

        // When
        val actual = delegate.handleCommand(
            action = ExplorationTrackingForegroundService.ACTION_START,
            startForeground = { startedForeground = true },
            stopService = {},
        )

        // Then
        actual shouldBe android.app.Service.START_NOT_STICKY
        startedForeground shouldBe true
        verify(exactly = 1) { runtime.startIfNeeded() }
    }

    @Test
    fun `handleCommand should stop runtime and service when stop action arrives`() {
        // Given
        val runtime = mockk<ExplorationTrackingRuntime>()
        val delegate = ExplorationTrackingServiceDelegate(runtime)
        var serviceStopped = false

        every { runtime.stop() } just runs

        // When
        val actual = delegate.handleCommand(
            action = ExplorationTrackingForegroundService.ACTION_STOP,
            startForeground = {},
            stopService = { serviceStopped = true },
        )

        // Then
        actual shouldBe android.app.Service.START_NOT_STICKY
        serviceStopped shouldBe true
        verify(exactly = 1) { runtime.stop() }
    }

    @Test
    fun `handleCommand should ignore unknown actions`() {
        // Given
        val runtime = mockk<ExplorationTrackingRuntime>()
        val delegate = ExplorationTrackingServiceDelegate(runtime)

        // When
        val actual = delegate.handleCommand(
            action = "unknown",
            startForeground = {},
            stopService = {},
        )

        // Then
        actual shouldBe android.app.Service.START_NOT_STICKY
        verify(exactly = 0) { runtime.startIfNeeded() }
        verify(exactly = 0) { runtime.stop() }
    }
}
