package com.github.arhor.journey.feature.map.location

import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.UserLocationFix
import com.github.arhor.journey.feature.map.CurrentLocationUiModel
import com.github.arhor.journey.feature.map.DEFAULT_CAMERA_BEARING
import com.github.arhor.journey.feature.map.DEFAULT_CAMERA_ZOOM
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import javax.inject.Inject

class CurrentLocationPresenter @Inject constructor(
    private val locationStabilizer: LocationStabilizer,
) {

    internal fun presentStabilizationTarget(
        trackingSession: ExplorationTrackingSession,
    ): LocationStabilizationSnapshot {
        val rawLocationFix = trackingSession.lastKnownLocationFix
            ?: trackingSession.lastKnownLocation?.toUserLocationFix()
        val shouldExposeLocation = trackingSession.status !in LOCATION_HIDDEN_STATUSES
        val locationFix = rawLocationFix.takeIf { shouldExposeLocation }

        if (locationFix == null) {
            return presentUnavailableStabilizationTarget()
        }

        return locationStabilizer.stabilize(locationFix)
    }

    internal fun presentUnavailableStabilizationTarget(): LocationStabilizationSnapshot {
        locationStabilizer.reset()
        return LocationStabilizationSnapshot(
            visualLocation = null,
            cameraLocation = null,
        )
    }

    internal fun presentMapLocation(
        animatedLocationSnapshot: MapLocationAnimationSnapshot,
        cameraPosition: CameraPositionState?,
        cameraUpdateOrigin: CameraUpdateOrigin,
        isUserInteractingCamera: Boolean,
    ): CurrentLocationPresentation {
        val currentLocation = animatedLocationSnapshot.visualLocation?.toCurrentLocationUiModel()
        val userLocation = currentLocation?.position
        val cameraLocation = animatedLocationSnapshot.cameraLocation?.location?.toLatLng()
        val resolvedCameraPosition = cameraLocation?.toCameraPosition(
            zoom = cameraPosition?.zoom ?: DEFAULT_CAMERA_ZOOM,
            bearing = cameraPosition?.bearing ?: DEFAULT_CAMERA_BEARING,
        )
            ?: cameraPosition
        val resolvedCameraUpdateOrigin = if (userLocation != null && !isUserInteractingCamera) {
            CameraUpdateOrigin.PROGRAMMATIC
        } else {
            cameraUpdateOrigin
        }

        return CurrentLocationPresentation(
            cameraPosition = resolvedCameraPosition,
            cameraUpdateOrigin = resolvedCameraUpdateOrigin,
            currentLocation = currentLocation,
            cameraLocation = cameraLocation,
            userLocation = userLocation,
        )
    }

    private fun GeoPoint.toUserLocationFix(): UserLocationFix =
        UserLocationFix(location = this)

    private fun GeoPoint.toLatLng(): LatLng =
        LatLng(
            latitude = lat,
            longitude = lon,
        )

    private fun UserLocationFix.toCurrentLocationUiModel(): CurrentLocationUiModel =
        CurrentLocationUiModel(
            position = location.toLatLng(),
            horizontalAccuracyMeters = horizontalAccuracyMeters,
            speedMetersPerSecond = speedMetersPerSecond,
            bearingDegrees = bearingDegrees,
            bearingAccuracyDegrees = bearingAccuracyDegrees,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
        )

    private fun LatLng.toCameraPosition(
        zoom: Double = DEFAULT_CAMERA_ZOOM,
        bearing: Double = DEFAULT_CAMERA_BEARING,
    ): CameraPositionState =
        CameraPositionState(
            target = this,
            zoom = zoom,
            bearing = bearing,
        )

    private companion object {
        val LOCATION_HIDDEN_STATUSES = setOf(
            ExplorationTrackingStatus.PERMISSION_DENIED,
            ExplorationTrackingStatus.LOCATION_SERVICES_DISABLED,
        )
    }
}

internal data class CurrentLocationPresentation(
    val cameraPosition: CameraPositionState?,
    val cameraUpdateOrigin: CameraUpdateOrigin,
    val currentLocation: CurrentLocationUiModel?,
    val cameraLocation: LatLng?,
    val userLocation: LatLng?,
)
