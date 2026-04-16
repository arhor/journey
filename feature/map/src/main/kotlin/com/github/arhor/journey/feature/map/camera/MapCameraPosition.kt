package com.github.arhor.journey.feature.map.camera

import com.github.arhor.journey.feature.map.DEFAULT_CAMERA_BEARING
import com.github.arhor.journey.feature.map.DEFAULT_CAMERA_ZOOM
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position
import kotlin.math.abs

internal const val CAMERA_SETTLE_DEBOUNCE_MS = 100L

private const val CAMERA_SETTLE_COORDINATE_THRESHOLD = 0.0001
private const val CAMERA_SETTLE_ZOOM_THRESHOLD = 0.01
private const val CAMERA_SETTLE_BEARING_THRESHOLD = 0.1
private const val CAMERA_SETTLE_TILT_THRESHOLD = 0.1

internal fun resolveProgrammaticCameraFollowUpdate(
    target: CameraPositionState?,
    origin: CameraUpdateOrigin,
    current: CameraPosition,
    isCameraMoving: Boolean,
    moveReason: CameraMoveReason,
): CameraPosition? {
    if (target == null || origin != CameraUpdateOrigin.PROGRAMMATIC) {
        return null
    }

    if (isCameraMoving && moveReason == CameraMoveReason.GESTURE) {
        return null
    }

    val targetPosition = current.copy(
        target = Position(
            latitude = target.target.latitude,
            longitude = target.target.longitude,
        ),
        zoom = target.zoom,
        bearing = target.bearing,
    )

    return targetPosition.takeUnless { areCameraPositionsEquivalent(current, it) }
}

internal fun resolveInitialMapCameraPosition(position: CameraPositionState?): CameraPosition =
    position?.toCameraPosition()
        ?: CameraPosition(
            zoom = DEFAULT_CAMERA_ZOOM,
            bearing = DEFAULT_CAMERA_BEARING,
        )

internal fun areCameraPositionsEquivalent(a: CameraPosition, b: CameraPosition): Boolean {
    return abs(a.target.latitude - b.target.latitude) < CAMERA_SETTLE_COORDINATE_THRESHOLD
        && abs(a.target.longitude - b.target.longitude) < CAMERA_SETTLE_COORDINATE_THRESHOLD
        && abs(a.zoom - b.zoom) < CAMERA_SETTLE_ZOOM_THRESHOLD
        && abs(a.bearing - b.bearing) < CAMERA_SETTLE_BEARING_THRESHOLD
        && abs(a.tilt - b.tilt) < CAMERA_SETTLE_TILT_THRESHOLD
}

internal fun CameraPosition.toCameraPositionState(): CameraPositionState =
    CameraPositionState(
        target = LatLng(
            latitude = target.latitude,
            longitude = target.longitude,
        ),
        zoom = zoom,
        bearing = bearing,
    )

internal fun CameraPositionState.toCameraPosition(): CameraPosition = CameraPosition(
    target = Position(
        latitude = target.latitude,
        longitude = target.longitude,
    ),
    zoom = zoom,
    bearing = bearing,
)

internal fun CameraMoveReason.toCameraUpdateOrigin(): CameraUpdateOrigin =
    if (this == CameraMoveReason.GESTURE) {
        CameraUpdateOrigin.USER
    } else {
        CameraUpdateOrigin.PROGRAMMATIC
    }
