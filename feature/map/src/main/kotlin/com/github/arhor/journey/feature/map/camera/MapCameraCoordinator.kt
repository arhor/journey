package com.github.arhor.journey.feature.map.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.github.arhor.journey.feature.map.DEFAULT_CAMERA_BEARING
import com.github.arhor.journey.feature.map.MapUiState
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.Position
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val USER_LOCATION_TIMEOUT = 5.seconds
private val NORTH_RESET_ANIMATION_DURATION = 600.milliseconds

@Composable
internal fun MapCameraCoordinator(
    cameraState: CameraState,
    target: CameraPositionState?,
    origin: CameraUpdateOrigin,
    northResetRequestToken: Int,
    recenterTargetLocation: LatLng?,
    onCurrentLocationUnavailable: () -> Unit,
) {
    val latestRecenterLocation by rememberUpdatedState(recenterTargetLocation)
    val latestOnCurrentLocationUnavailable by rememberUpdatedState(onCurrentLocationUnavailable)
    var lastNorthResetCameraFollowSkipToken by remember {
        mutableIntStateOf(northResetRequestToken)
    }
    var activeNorthResetToken by remember {
        mutableIntStateOf(0)
    }
    val isCameraGestureMoving = cameraState.isCameraMoving && cameraState.moveReason == CameraMoveReason.GESTURE

    LaunchedEffect(
        target,
        origin,
        northResetRequestToken,
        activeNorthResetToken,
        isCameraGestureMoving,
        cameraState,
    ) {
        if (northResetRequestToken != lastNorthResetCameraFollowSkipToken) {
            lastNorthResetCameraFollowSkipToken = northResetRequestToken
            return@LaunchedEffect
        }

        if (activeNorthResetToken > 0 && activeNorthResetToken == northResetRequestToken) {
            return@LaunchedEffect
        }

        val targetPosition = resolveProgrammaticCameraFollowUpdate(
            target = target,
            origin = origin,
            current = cameraState.position,
            isCameraMoving = cameraState.isCameraMoving,
            moveReason = cameraState.moveReason,
        ) ?: return@LaunchedEffect

        cameraState.position = targetPosition
    }

    LaunchedEffect(northResetRequestToken) {
        val requestToken = northResetRequestToken
        if (requestToken <= 0) {
            return@LaunchedEffect
        }

        activeNorthResetToken = requestToken

        try {
            val location = latestRecenterLocation ?: withTimeoutOrNull(USER_LOCATION_TIMEOUT) {
                snapshotFlow { latestRecenterLocation }
                    .filterNotNull()
                    .first()
            }

            if (location == null) {
                latestOnCurrentLocationUnavailable()
                return@LaunchedEffect
            }

            cameraState.animateTo(
                finalPosition = cameraState.position.copy(
                    target = Position(
                        latitude = location.latitude,
                        longitude = location.longitude,
                    ),
                    zoom = target?.zoom ?: cameraState.position.zoom,
                    bearing = DEFAULT_CAMERA_BEARING,
                ),
                duration = NORTH_RESET_ANIMATION_DURATION,
            )
        } finally {
            if (activeNorthResetToken == requestToken) {
                activeNorthResetToken = 0
            }
        }
    }
}

internal fun MapUiState.Content.recenterTargetLocation(): LatLng? =
    currentLocation?.position ?: cameraLocation
