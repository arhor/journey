package com.github.arhor.journey.feature.map.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.github.arhor.journey.domain.model.GeoBounds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.BoundingBox
import kotlin.math.abs

private const val CAMERA_SETTLE_BOUNDS_THRESHOLD = 0.0001

@Composable
internal fun MapViewportReporter(
    cameraState: CameraState,
    restartKey: Any?,
    onViewportChanged: (GeoBounds) -> Unit,
) {
    val latestOnViewportChanged by rememberUpdatedState(onViewportChanged)

    LaunchedEffect(restartKey, cameraState) {
        snapshotFlow {
            cameraState.position
            cameraState.projection?.queryVisibleBoundingBox()?.toGeoBounds()?.let { visibleBounds ->
                CameraViewportSnapshot(
                    visibleBounds = visibleBounds,
                    isCameraMoving = cameraState.isCameraMoving,
                    moveReason = cameraState.moveReason,
                )
            }
        }
            .filterNotNull()
            .cameraViewportEvents()
            .collectLatest { visibleBounds ->
                latestOnViewportChanged(visibleBounds)
            }
    }
}

internal fun Flow<CameraViewportSnapshot>.cameraViewportEvents(): Flow<GeoBounds> =
    filter(::shouldPublishCameraViewportSnapshot)
        .map { it.visibleBounds }
        .distinctUntilChanged(::areGeoBoundsEquivalent)

internal fun shouldPublishCameraViewportSnapshot(snapshot: CameraViewportSnapshot): Boolean =
    !snapshot.isCameraMoving || snapshot.moveReason == CameraMoveReason.GESTURE

private fun areGeoBoundsEquivalent(a: GeoBounds, b: GeoBounds): Boolean {
    return abs(a.south - b.south) < CAMERA_SETTLE_BOUNDS_THRESHOLD
        && abs(a.west - b.west) < CAMERA_SETTLE_BOUNDS_THRESHOLD
        && abs(a.north - b.north) < CAMERA_SETTLE_BOUNDS_THRESHOLD
        && abs(a.east - b.east) < CAMERA_SETTLE_BOUNDS_THRESHOLD
}

private fun BoundingBox.toGeoBounds(): GeoBounds = GeoBounds(
    south = south,
    west = west,
    north = north,
    east = east,
)

internal data class CameraViewportSnapshot(
    val visibleBounds: GeoBounds,
    val isCameraMoving: Boolean,
    val moveReason: CameraMoveReason,
)
