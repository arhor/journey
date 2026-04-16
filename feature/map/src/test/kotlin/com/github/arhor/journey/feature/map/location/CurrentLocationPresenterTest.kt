package com.github.arhor.journey.feature.map.location

import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.UserLocationFix
import com.github.arhor.journey.feature.map.CurrentLocationUiModel
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import io.kotest.matchers.shouldBe
import org.junit.Test

class CurrentLocationPresenterTest {

    @Test
    fun `presentStabilizationTarget should expose stabilized fix when tracking session has visible location`() {
        // Given
        val subject = CurrentLocationPresenter(LocationStabilizer(LocationStabilizerConfig()))
        val fix = locationFix(lat = 50.45, lon = 30.52)
        val session = ExplorationTrackingSession(
            status = ExplorationTrackingStatus.TRACKING,
            lastKnownLocation = fix.location,
            lastKnownLocationFix = fix,
        )

        // When
        val actual = subject.presentStabilizationTarget(session)

        // Then
        actual.visualLocation shouldBe fix
        actual.cameraLocation shouldBe fix
    }

    @Test
    fun `presentStabilizationTarget should hide and reset location when tracking status blocks exposure`() {
        // Given
        val subject = CurrentLocationPresenter(LocationStabilizer(LocationStabilizerConfig()))
        val fix = locationFix(lat = 50.45, lon = 30.52)
        val visibleSession = ExplorationTrackingSession(
            status = ExplorationTrackingStatus.TRACKING,
            lastKnownLocation = fix.location,
            lastKnownLocationFix = fix,
        )
        subject.presentStabilizationTarget(visibleSession)

        // When
        val hidden = subject.presentStabilizationTarget(
            visibleSession.copy(status = ExplorationTrackingStatus.PERMISSION_DENIED),
        )
        val recovered = subject.presentStabilizationTarget(visibleSession)

        // Then
        hidden shouldBe LocationStabilizationSnapshot(
            visualLocation = null,
            cameraLocation = null,
        )
        recovered.visualLocation shouldBe fix
        recovered.cameraLocation shouldBe fix
    }

    @Test
    fun `presentMapLocation should derive current location camera location and programmatic camera update`() {
        // Given
        val subject = CurrentLocationPresenter(LocationStabilizer(LocationStabilizerConfig()))
        val visualFix = locationFix(
            lat = 50.45,
            lon = 30.52,
            accuracy = 8.0,
            speed = 2.0,
            bearing = 90.0,
            bearingAccuracy = 12.0,
            elapsedRealtimeNanos = 123L,
        )
        val cameraFix = locationFix(lat = 50.46, lon = 30.53)
        val previousCameraPosition = CameraPositionState(
            target = LatLng(latitude = 1.0, longitude = 2.0),
            zoom = 15.5,
            bearing = 33.0,
        )

        // When
        val actual = subject.presentMapLocation(
            animatedLocationSnapshot = MapLocationAnimationSnapshot(
                visualLocation = visualFix,
                cameraLocation = cameraFix,
            ),
            cameraPosition = previousCameraPosition,
            cameraUpdateOrigin = CameraUpdateOrigin.USER,
            isUserInteractingCamera = false,
        )

        // Then
        actual.currentLocation shouldBe CurrentLocationUiModel(
            position = LatLng(latitude = 50.45, longitude = 30.52),
            horizontalAccuracyMeters = 8.0,
            speedMetersPerSecond = 2.0,
            bearingDegrees = 90.0,
            bearingAccuracyDegrees = 12.0,
            elapsedRealtimeNanos = 123L,
        )
        actual.userLocation shouldBe LatLng(latitude = 50.45, longitude = 30.52)
        actual.cameraLocation shouldBe LatLng(latitude = 50.46, longitude = 30.53)
        actual.cameraPosition shouldBe CameraPositionState(
            target = LatLng(latitude = 50.46, longitude = 30.53),
            zoom = 15.5,
            bearing = 33.0,
        )
        actual.cameraUpdateOrigin shouldBe CameraUpdateOrigin.PROGRAMMATIC
    }

    @Test
    fun `presentMapLocation should preserve camera state and origin when location is unavailable`() {
        // Given
        val subject = CurrentLocationPresenter(LocationStabilizer(LocationStabilizerConfig()))
        val previousCameraPosition = CameraPositionState(
            target = LatLng(latitude = 1.0, longitude = 2.0),
            zoom = 15.5,
            bearing = 33.0,
        )

        // When
        val actual = subject.presentMapLocation(
            animatedLocationSnapshot = MapLocationAnimationSnapshot(),
            cameraPosition = previousCameraPosition,
            cameraUpdateOrigin = CameraUpdateOrigin.USER,
            isUserInteractingCamera = true,
        )

        // Then
        actual.currentLocation shouldBe null
        actual.userLocation shouldBe null
        actual.cameraLocation shouldBe null
        actual.cameraPosition shouldBe previousCameraPosition
        actual.cameraUpdateOrigin shouldBe CameraUpdateOrigin.USER
    }

    private fun locationFix(
        lat: Double,
        lon: Double,
        accuracy: Double = 10.0,
        speed: Double? = null,
        bearing: Double? = null,
        bearingAccuracy: Double? = null,
        elapsedRealtimeNanos: Long? = null,
    ): UserLocationFix =
        UserLocationFix(
            location = GeoPoint(lat = lat, lon = lon),
            horizontalAccuracyMeters = accuracy,
            speedMetersPerSecond = speed,
            bearingDegrees = bearing,
            bearingAccuracyDegrees = bearingAccuracy,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
        )
}
