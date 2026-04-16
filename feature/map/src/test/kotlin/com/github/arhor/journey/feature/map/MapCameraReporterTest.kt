package com.github.arhor.journey.feature.map

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.feature.map.camera.CameraSettledSnapshot
import com.github.arhor.journey.feature.map.camera.CameraViewportSnapshot
import com.github.arhor.journey.feature.map.camera.cameraSettledEvents
import com.github.arhor.journey.feature.map.camera.cameraViewportEvents
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position

class MapCameraReporterTest {

    @Test
    fun `cameraViewportEvents should publish gesture and settled bounds while suppressing moving programmatic bounds`() =
        runTest {
            // Given
            val input = MutableSharedFlow<CameraViewportSnapshot>(extraBufferCapacity = 8)
            val actual = mutableListOf<GeoBounds>()
            val collector = backgroundScope.launch {
                input.cameraViewportEvents().collect { actual += it }
            }
            val movingProgrammatic = bounds(south = 40.0, west = -75.0, north = 41.0, east = -74.0)
            val gesture = bounds(south = 41.0, west = -75.0, north = 42.0, east = -74.0)
            val settled = bounds(south = 42.0, west = -75.0, north = 43.0, east = -74.0)

            // When
            runCurrent()
            input.emit(
                CameraViewportSnapshot(
                    visibleBounds = movingProgrammatic,
                    isCameraMoving = true,
                    moveReason = CameraMoveReason.PROGRAMMATIC,
                ),
            )
            input.emit(
                CameraViewportSnapshot(
                    visibleBounds = gesture,
                    isCameraMoving = true,
                    moveReason = CameraMoveReason.GESTURE,
                ),
            )
            input.emit(
                CameraViewportSnapshot(
                    visibleBounds = settled,
                    isCameraMoving = false,
                    moveReason = CameraMoveReason.PROGRAMMATIC,
                ),
            )
            runCurrent()

            // Then
            actual shouldBe listOf(gesture, settled)

            collector.cancel()
        }

    @Test
    fun `cameraViewportEvents should suppress equivalent consecutive bounds`() = runTest {
        // Given
        val input = MutableSharedFlow<CameraViewportSnapshot>(extraBufferCapacity = 8)
        val actual = mutableListOf<GeoBounds>()
        val collector = backgroundScope.launch {
            input.cameraViewportEvents().collect { actual += it }
        }
        val initial = bounds(south = 40.0, west = -75.0, north = 41.0, east = -74.0)
        val equivalent = bounds(south = 40.00005, west = -75.00005, north = 41.00005, east = -74.00005)

        // When
        runCurrent()
        input.emit(viewportSnapshot(initial))
        input.emit(viewportSnapshot(equivalent))
        runCurrent()

        // Then
        actual shouldBe listOf(initial)

        collector.cancel()
    }

    @Test
    fun `cameraSettledEvents should debounce and publish only settled snapshots`() = runTest {
        // Given
        val input = MutableSharedFlow<CameraSettledSnapshot>(extraBufferCapacity = 8)
        val actual = mutableListOf<CameraSettledSnapshot>()
        val collector = backgroundScope.launch {
            input.cameraSettledEvents(debounceMillis = 100L).collect { actual += it }
        }
        val moving = settledSnapshot(latitude = 40.7128, longitude = -74.0060, isCameraMoving = true)
        val settled = settledSnapshot(latitude = 40.7306, longitude = -73.9352, isCameraMoving = false)

        // When
        runCurrent()
        input.emit(moving)
        advanceTimeBy(100L)
        runCurrent()
        input.emit(settled)
        advanceTimeBy(99L)
        runCurrent()
        val beforeDebounceElapsed = actual.toList()
        advanceTimeBy(1L)
        runCurrent()

        // Then
        beforeDebounceElapsed shouldBe emptyList()
        actual shouldBe listOf(settled)

        collector.cancel()
    }

    @Test
    fun `cameraSettledEvents should suppress equivalent consecutive settled snapshots`() = runTest {
        // Given
        val input = MutableSharedFlow<CameraSettledSnapshot>(extraBufferCapacity = 8)
        val actual = mutableListOf<CameraSettledSnapshot>()
        val collector = backgroundScope.launch {
            input.cameraSettledEvents(debounceMillis = 100L).collect { actual += it }
        }
        val initial = settledSnapshot(latitude = 40.7128, longitude = -74.0060, zoom = 17.0, bearing = 10.0)
        val equivalent = settledSnapshot(latitude = 40.71285, longitude = -74.00605, zoom = 17.005, bearing = 10.05)

        // When
        runCurrent()
        input.emit(initial)
        advanceTimeBy(100L)
        runCurrent()
        input.emit(equivalent)
        advanceTimeBy(100L)
        runCurrent()

        // Then
        actual shouldBe listOf(initial)

        collector.cancel()
    }

    private fun viewportSnapshot(bounds: GeoBounds): CameraViewportSnapshot =
        CameraViewportSnapshot(
            visibleBounds = bounds,
            isCameraMoving = false,
            moveReason = CameraMoveReason.PROGRAMMATIC,
        )

    private fun settledSnapshot(
        latitude: Double,
        longitude: Double,
        zoom: Double = 17.0,
        bearing: Double = 10.0,
        isCameraMoving: Boolean = false,
        origin: CameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
    ): CameraSettledSnapshot =
        CameraSettledSnapshot(
            position = CameraPosition(
                target = Position(latitude = latitude, longitude = longitude),
                zoom = zoom,
                bearing = bearing,
            ),
            origin = origin,
            isCameraMoving = isCameraMoving,
        )

    private fun bounds(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ): GeoBounds =
        GeoBounds(
            south = south,
            west = west,
            north = north,
            east = east,
        )
}
