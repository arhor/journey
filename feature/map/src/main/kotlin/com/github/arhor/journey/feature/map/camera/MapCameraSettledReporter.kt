package com.github.arhor.journey.feature.map.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState

@Composable
internal fun MapCameraSettledReporter(
    cameraState: CameraState,
    restartKey: Any?,
    onCameraSettled: (CameraPositionState, CameraUpdateOrigin) -> Unit,
) {
    val latestOnCameraSettled by rememberUpdatedState(onCameraSettled)

    LaunchedEffect(restartKey, cameraState) {
        snapshotFlow {
            CameraSettledSnapshot(
                position = cameraState.position,
                origin = cameraState.moveReason.toCameraUpdateOrigin(),
                isCameraMoving = cameraState.isCameraMoving,
            )
        }
            .cameraSettledEvents()
            .collectLatest { settled ->
                latestOnCameraSettled(
                    settled.position.toCameraPositionState(),
                    settled.origin,
                )
            }
    }
}

internal fun Flow<CameraSettledSnapshot>.cameraSettledEvents(
    debounceMillis: Long = CAMERA_SETTLE_DEBOUNCE_MS,
): Flow<CameraSettledSnapshot> =
    debounce(debounceMillis)
        .filter { !it.isCameraMoving }
        .distinctUntilChanged(::areCameraSettledSnapshotsEquivalent)

private fun areCameraSettledSnapshotsEquivalent(
    a: CameraSettledSnapshot,
    b: CameraSettledSnapshot,
): Boolean {
    return a.origin == b.origin &&
        a.isCameraMoving == b.isCameraMoving &&
        areCameraPositionsEquivalent(a.position, b.position)
}

internal data class CameraSettledSnapshot(
    val position: CameraPosition,
    val origin: CameraUpdateOrigin,
    val isCameraMoving: Boolean,
)
