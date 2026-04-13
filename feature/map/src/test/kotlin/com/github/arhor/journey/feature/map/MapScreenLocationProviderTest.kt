package com.github.arhor.journey.feature.map

import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.feature.map.fow.model.FogOfWarUiState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import io.kotest.matchers.shouldBe
import org.junit.Test

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
}
