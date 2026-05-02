package com.github.arhor.journey.feature.map.viewinterop

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test

class MapStartupGateControllerTest {

    @Test
    fun `startup gate controller should emit one-shot location and frame callbacks per session`() {
        // Given
        val events = mutableListOf<String>()
        var timeoutRunnable: Runnable? = null
        var cancelledTimeouts = 0
        val controller = MapStartupGateController(
            sessionId = 13L,
            onMapSurfaceSessionStarted = { sessionId -> events += "started:$sessionId" },
            onFirstLocationFix = { sessionId -> events += "location:$sessionId" },
            onFirstMapFrameRendered = { sessionId -> events += "render:$sessionId" },
            onStartupTimeout = { sessionId -> events += "timeout:$sessionId" },
            scheduleTimeout = { _, runnable -> timeoutRunnable = runnable },
            cancelTimeout = { _ -> cancelledTimeouts += 1 },
        )

        controller.attachToMapView(timeoutMillis = 5_000L)

        // When
        controller.onFirstLocationFixAcquired()
        controller.onFirstLocationFixAcquired()
        controller.onFirstMapFrameRendered()
        controller.onFirstMapFrameRendered()
        timeoutRunnable?.run()

        // Then
        events.shouldContainExactly(
            "started:13",
            "location:13",
            "render:13",
        )
        cancelledTimeouts shouldBe 1
    }

    @Test
    fun `startup gate controller should emit timeout only once when readiness is not complete`() {
        // Given
        val events = mutableListOf<String>()
        var timeoutRunnable: Runnable? = null
        val controller = MapStartupGateController(
            sessionId = 21L,
            onMapSurfaceSessionStarted = { sessionId -> events += "started:$sessionId" },
            onFirstLocationFix = { sessionId -> events += "location:$sessionId" },
            onFirstMapFrameRendered = { sessionId -> events += "render:$sessionId" },
            onStartupTimeout = { sessionId -> events += "timeout:$sessionId" },
            scheduleTimeout = { _, runnable -> timeoutRunnable = runnable },
            cancelTimeout = {},
        )

        controller.attachToMapView(timeoutMillis = 5_000L)

        // When
        timeoutRunnable?.run()
        timeoutRunnable?.run()

        // Then
        events.shouldContainExactly(
            "started:21",
            "timeout:21",
        )
    }

    @Test
    fun `startup gate controller should re-arm on new session and ignore callbacks after cleanup`() {
        // Given
        val events = mutableListOf<String>()
        var firstSessionRunnable: Runnable? = null
        var secondSessionRunnable: Runnable? = null

        val firstSessionController = MapStartupGateController(
            sessionId = 1L,
            onMapSurfaceSessionStarted = { sessionId -> events += "started:$sessionId" },
            onFirstLocationFix = { sessionId -> events += "location:$sessionId" },
            onFirstMapFrameRendered = { sessionId -> events += "render:$sessionId" },
            onStartupTimeout = { sessionId -> events += "timeout:$sessionId" },
            scheduleTimeout = { _, runnable -> firstSessionRunnable = runnable },
            cancelTimeout = {},
        )

        firstSessionController.attachToMapView(timeoutMillis = 5_000L)
        firstSessionController.cleanup()

        // When
        firstSessionRunnable?.run()

        val secondSessionController = MapStartupGateController(
            sessionId = 2L,
            onMapSurfaceSessionStarted = { sessionId -> events += "started:$sessionId" },
            onFirstLocationFix = { sessionId -> events += "location:$sessionId" },
            onFirstMapFrameRendered = { sessionId -> events += "render:$sessionId" },
            onStartupTimeout = { sessionId -> events += "timeout:$sessionId" },
            scheduleTimeout = { _, runnable -> secondSessionRunnable = runnable },
            cancelTimeout = {},
        )

        secondSessionController.attachToMapView(timeoutMillis = 5_000L)
        secondSessionController.onFirstLocationFixAcquired()
        secondSessionController.onFirstMapFrameRendered()
        secondSessionRunnable?.run()

        // Then
        events.shouldContainExactly(
            "started:1",
            "started:2",
            "location:2",
            "render:2",
        )
    }
}
