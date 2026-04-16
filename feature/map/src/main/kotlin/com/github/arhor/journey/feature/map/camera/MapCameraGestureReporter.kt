package com.github.arhor.journey.feature.map.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.github.arhor.journey.feature.map.model.CameraPositionState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraState

@Composable
internal fun MapCameraGestureReporter(
    cameraState: CameraState,
    restartKey: Any?,
    onGestureStarted: (CameraPositionState) -> Unit,
) {
    val latestOnGestureStarted by rememberUpdatedState(onGestureStarted)

    LaunchedEffect(restartKey, cameraState) {
        snapshotFlow { cameraState.isCameraMoving to cameraState.moveReason }
            .distinctUntilChanged()
            .filter { (isCameraMoving, moveReason) ->
                isCameraMoving && moveReason == CameraMoveReason.GESTURE
            }
            .collectLatest {
                latestOnGestureStarted(cameraState.position.toCameraPositionState())
            }
    }
}
