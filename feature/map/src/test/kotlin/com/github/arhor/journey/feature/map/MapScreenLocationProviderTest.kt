package com.github.arhor.journey.feature.map

import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.feature.map.fow.model.FogOfWarUiState
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position

class MapScreenLocationProviderTest {

    @Test
    fun `toMapLibreLocation should preserve stabilized location metadata`() {
        // Given
        val source = currentLocation(
            position = LatLng(latitude = 40.7128, longitude = -74.0060),
            accuracy = 18.0,
            speed = 2.5,
            bearing = 45.0,
            bearingAccuracy = 8.0,
            elapsedRealtimeNanos = 1_000_000_000L,
        )

        // When
        val actual = source.toMapLibreLocation(
            nowElapsedRealtimeNanos = { 2_000_000_000L },
        )

        // Then
        actual.position.latitude shouldBe 40.7128
        actual.position.longitude shouldBe -74.0060
        actual.accuracy shouldBe 18.0
        actual.speed shouldBe 2.5
        actual.bearing shouldBe 45.0
        actual.bearingAccuracy shouldBe 8.0
        (actual.timestamp.elapsedNow().inWholeMilliseconds >= 1_000L) shouldBe true
    }

    @Test
    fun `MapUiLocationProvider should expose updated MapLibre location`() {
        // Given
        val location = currentLocation(
            position = LatLng(latitude = 40.7128, longitude = -74.0060),
            accuracy = 18.0,
        ).toMapLibreLocation(
            nowElapsedRealtimeNanos = { 1_000L },
        )
        val subject = MapUiLocationProvider(initialLocation = null)

        // When
        subject.update(location)

        // Then
        subject.location.value shouldBe location
    }

    @Test
    fun `recenterTargetLocation should prefer visual location over camera follow location`() {
        // Given
        val visualLocation = LatLng(latitude = 40.7128, longitude = -74.0060)
        val cameraLocation = LatLng(latitude = 40.7000, longitude = -74.0000)
        val state = contentState(
            currentLocation = currentLocation(position = visualLocation, accuracy = 80.0),
            cameraLocation = cameraLocation,
        )

        // When
        val actual = state.recenterTargetLocation()

        // Then
        actual shouldBe visualLocation
    }

    @Test
    fun `recenterTargetLocation should fall back to camera follow location when visual location is missing`() {
        // Given
        val cameraLocation = LatLng(latitude = 40.7000, longitude = -74.0000)
        val state = contentState(
            currentLocation = null,
            cameraLocation = cameraLocation,
        )

        // When
        val actual = state.recenterTargetLocation()

        // Then
        actual shouldBe cameraLocation
    }

    @Test
    fun `resolveProgrammaticCameraFollowUpdate should return animated target when programmatic target changes`() {
        // Given
        val current = cameraPosition(latitude = 40.7128, longitude = -74.0060, zoom = 17.0, bearing = 10.0)
        val target = cameraPositionState(latitude = 40.7306, longitude = -73.9352, zoom = 17.0, bearing = 10.0)

        // When
        val actual = resolveProgrammaticCameraFollowUpdate(
            target = target,
            origin = CameraUpdateOrigin.PROGRAMMATIC,
            current = current,
            isCameraMoving = false,
            moveReason = CameraMoveReason.NONE,
        )

        // Then
        actual shouldBe cameraPosition(latitude = 40.7306, longitude = -73.9352, zoom = 17.0, bearing = 10.0)
    }

    @Test
    fun `resolveProgrammaticCameraFollowUpdate should return null when update originated from user`() {
        // Given
        val current = cameraPosition(latitude = 40.7128, longitude = -74.0060, zoom = 17.0, bearing = 10.0)
        val target = cameraPositionState(latitude = 40.7306, longitude = -73.9352, zoom = 17.0, bearing = 10.0)

        // When
        val actual = resolveProgrammaticCameraFollowUpdate(
            target = target,
            origin = CameraUpdateOrigin.USER,
            current = current,
            isCameraMoving = false,
            moveReason = CameraMoveReason.NONE,
        )

        // Then
        actual shouldBe null
    }

    @Test
    fun `resolveProgrammaticCameraFollowUpdate should return null while camera is moving from gesture`() {
        // Given
        val current = cameraPosition(latitude = 40.7128, longitude = -74.0060, zoom = 17.0, bearing = 10.0)
        val target = cameraPositionState(latitude = 40.7306, longitude = -73.9352, zoom = 17.0, bearing = 10.0)

        // When
        val actual = resolveProgrammaticCameraFollowUpdate(
            target = target,
            origin = CameraUpdateOrigin.PROGRAMMATIC,
            current = current,
            isCameraMoving = true,
            moveReason = CameraMoveReason.GESTURE,
        )

        // Then
        actual shouldBe null
    }

    @Test
    fun `resolveProgrammaticCameraFollowUpdate should return null when target is equivalent to current camera`() {
        // Given
        val current = cameraPosition(latitude = 40.7128, longitude = -74.0060, zoom = 17.0, bearing = 10.0)
        val target = cameraPositionState(latitude = 40.71285, longitude = -74.00605, zoom = 17.005, bearing = 10.05)

        // When
        val actual = resolveProgrammaticCameraFollowUpdate(
            target = target,
            origin = CameraUpdateOrigin.PROGRAMMATIC,
            current = current,
            isCameraMoving = false,
            moveReason = CameraMoveReason.NONE,
        )

        // Then
        actual shouldBe null
    }

    @Test
    fun `resolveProgrammaticCameraFollowUpdate should return animated target when zoom or bearing changes`() {
        // Given
        val current = cameraPosition(latitude = 40.7128, longitude = -74.0060, zoom = 17.0, bearing = 10.0)
        val target = cameraPositionState(latitude = 40.7128, longitude = -74.0060, zoom = 17.5, bearing = 25.0)

        // When
        val actual = resolveProgrammaticCameraFollowUpdate(
            target = target,
            origin = CameraUpdateOrigin.PROGRAMMATIC,
            current = current,
            isCameraMoving = false,
            moveReason = CameraMoveReason.NONE,
        )

        // Then
        actual shouldBe cameraPosition(latitude = 40.7128, longitude = -74.0060, zoom = 17.5, bearing = 25.0)
    }

    @Test
    fun `resolveProgrammaticCameraFollowUpdate should return animated target while programmatic animation is running`() {
        // Given
        val current = cameraPosition(latitude = 40.7128, longitude = -74.0060, zoom = 17.0, bearing = 10.0)
        val target = cameraPositionState(latitude = 40.7306, longitude = -73.9352, zoom = 17.0, bearing = 10.0)

        // When
        val actual = resolveProgrammaticCameraFollowUpdate(
            target = target,
            origin = CameraUpdateOrigin.PROGRAMMATIC,
            current = current,
            isCameraMoving = true,
            moveReason = CameraMoveReason.PROGRAMMATIC,
        )

        // Then
        actual shouldBe cameraPosition(latitude = 40.7306, longitude = -73.9352, zoom = 17.0, bearing = 10.0)
    }

    @Test
    fun `shouldPublishCameraViewportSnapshot should suppress moving programmatic snapshots`() {
        // Given
        val snapshot = cameraViewportSnapshot(
            isCameraMoving = true,
            moveReason = CameraMoveReason.PROGRAMMATIC,
        )

        // When
        val actual = shouldPublishCameraViewportSnapshot(snapshot)

        // Then
        actual shouldBe false
    }

    @Test
    fun `shouldPublishCameraViewportSnapshot should allow gesture and settled snapshots`() {
        // Given
        val gestureSnapshot = cameraViewportSnapshot(
            isCameraMoving = true,
            moveReason = CameraMoveReason.GESTURE,
        )
        val settledSnapshot = cameraViewportSnapshot(
            isCameraMoving = false,
            moveReason = CameraMoveReason.PROGRAMMATIC,
        )

        // When
        val actualGesture = shouldPublishCameraViewportSnapshot(gestureSnapshot)
        val actualSettled = shouldPublishCameraViewportSnapshot(settledSnapshot)

        // Then
        actualGesture shouldBe true
        actualSettled shouldBe true
    }

    @Test
    fun `resolveInitialMapCameraPosition should use app default zoom when view model camera is absent`() {
        // Given
        val position = null

        // When
        val actual = resolveInitialMapCameraPosition(position)

        // Then
        actual.zoom shouldBe DEFAULT_CAMERA_ZOOM
        actual.bearing shouldBe DEFAULT_CAMERA_BEARING
    }

    @Test
    fun `resolveInitialMapCameraPosition should preserve view model camera when present`() {
        // Given
        val position = cameraPositionState(
            latitude = 40.7128,
            longitude = -74.0060,
            zoom = 16.5,
            bearing = 25.0,
        )

        // When
        val actual = resolveInitialMapCameraPosition(position)

        // Then
        actual shouldBe cameraPosition(
            latitude = 40.7128,
            longitude = -74.0060,
            zoom = 16.5,
            bearing = 25.0,
        )
    }

    private fun contentState(
        currentLocation: CurrentLocationUiModel?,
        cameraLocation: LatLng?,
    ): MapUiState.Content =
        MapUiState.Content(
            cameraPosition = null,
            cameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
            northResetRequestToken = 0,
            currentLocation = currentLocation,
            cameraLocation = cameraLocation,
            userLocation = currentLocation?.position,
            isExplorationTrackingActive = true,
            explorationTrackingCadence = ExplorationTrackingCadence.FOREGROUND,
            explorationTrackingStatus = ExplorationTrackingStatus.TRACKING,
            selectedStyle = null,
            visibleObjects = emptyList(),
            selectedWatchtower = null,
            fogOfWar = FogOfWarUiState(),
        )

    private fun currentLocation(
        position: LatLng,
        accuracy: Double?,
        speed: Double? = null,
        bearing: Double? = null,
        bearingAccuracy: Double? = null,
        elapsedRealtimeNanos: Long? = null,
    ): CurrentLocationUiModel =
        CurrentLocationUiModel(
            position = position,
            horizontalAccuracyMeters = accuracy,
            speedMetersPerSecond = speed,
            bearingDegrees = bearing,
            bearingAccuracyDegrees = bearingAccuracy,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
        )

    private fun cameraPosition(
        latitude: Double,
        longitude: Double,
        zoom: Double,
        bearing: Double,
    ): CameraPosition =
        CameraPosition(
            target = Position(latitude = latitude, longitude = longitude),
            zoom = zoom,
            bearing = bearing,
        )

    private fun cameraPositionState(
        latitude: Double,
        longitude: Double,
        zoom: Double,
        bearing: Double,
    ): CameraPositionState =
        CameraPositionState(
            target = LatLng(latitude = latitude, longitude = longitude),
            zoom = zoom,
            bearing = bearing,
        )

    private fun cameraViewportSnapshot(
        isCameraMoving: Boolean,
        moveReason: CameraMoveReason,
    ): CameraViewportSnapshot =
        CameraViewportSnapshot(
            visibleBounds = GeoBounds(
                south = 40.0,
                west = -75.0,
                north = 41.0,
                east = -74.0,
            ),
            isCameraMoving = isCameraMoving,
            moveReason = moveReason,
        )
}
