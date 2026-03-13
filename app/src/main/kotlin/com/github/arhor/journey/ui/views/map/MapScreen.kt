package com.github.arhor.journey.ui.views.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.ui.components.ErrorMessage
import com.github.arhor.journey.ui.components.LoadingIndicator
import com.github.arhor.journey.ui.views.map.model.CameraPositionState
import com.github.arhor.journey.ui.views.map.model.CameraUpdateOrigin
import com.github.arhor.journey.ui.views.map.model.LatLng
import com.github.arhor.journey.ui.views.map.renderer.MapObjectsRendererAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.UserLocationState
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.rememberStyleState
import org.maplibre.spatialk.geojson.Position
import kotlin.math.absoluteValue

@Composable
fun MapScreen(
    state: MapUiState,
    userLocationState: UserLocationState?,
    dispatch: (MapIntent) -> Unit,
) {
    when (state) {
        is MapUiState.Loading -> LoadingIndicator()
        is MapUiState.Failure -> ErrorMessage(message = state.errorMessage)
        is MapUiState.Content -> MapContent(
            state = state,
            userLocationState = userLocationState,
            dispatch = dispatch,
        )
    }
}

@Composable
internal fun MapContent(
    state: MapUiState.Content,
    userLocationState: UserLocationState?,
    dispatch: (MapIntent) -> Unit,
) {
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(
                latitude = state.cameraPosition.target.latitude,
                longitude = state.cameraPosition.target.longitude,
            ),
            zoom = state.cameraPosition.zoom,
        ),
    )
    val styleState = rememberStyleState()

    LaunchedEffect(state.cameraPosition, state.cameraUpdateOrigin) {
        if (state.cameraUpdateOrigin != CameraUpdateOrigin.PROGRAMMATIC) {
            return@LaunchedEffect
        }

        val current = cameraState.position
        if (
            current.target.latitude != state.cameraPosition.target.latitude ||
            current.target.longitude != state.cameraPosition.target.longitude ||
            current.zoom != state.cameraPosition.zoom
        ) {
            cameraState.position = current.copy(
                target = Position(
                    latitude = state.cameraPosition.target.latitude,
                    longitude = state.cameraPosition.target.longitude,
                ),
                zoom = state.cameraPosition.zoom,
            )
        }
    }

    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.position }
            .debounce(CAMERA_SETTLE_DEBOUNCE_MS)
            .distinctUntilChanged(::areCameraPositionsEquivalent)
            .collectLatest { position ->
                dispatch(
                    MapIntent.CameraSettled(
                        position = CameraPositionState(
                            target = LatLng(
                                latitude = position.target.latitude,
                                longitude = position.target.longitude,
                            ),
                            zoom = position.zoom,
                        ),
                        origin = CameraUpdateOrigin.USER,
                    ),
                )
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        state.selectedStyle?.let {
            key(it) {
                MaplibreMap(
                    modifier = Modifier.fillMaxSize(),
                    baseStyle = when (it.type) {
                        MapStyle.Type.BUNDLE -> BaseStyle.Json(it.value)
                        MapStyle.Type.REMOTE -> BaseStyle.Uri(it.value)
                    },
                    cameraState = cameraState,
                    styleState = styleState,
                    options = MapOptions(
                        gestureOptions = GestureOptions.Standard,
                        ornamentOptions = OrnamentOptions.AllDisabled,
                    ),
                    onMapClick = { position, _ ->
                        dispatch(
                            MapIntent.MapTapped(
                                target = LatLng(
                                    latitude = position.latitude,
                                    longitude = position.longitude,
                                ),
                            ),
                        )
                        org.maplibre.compose.util.ClickResult.Pass
                    },
                    onMapLoadFailed = { error ->
                        dispatch(MapIntent.MapLoadFailed(error))
                    },
                    content = {
                        if (userLocationState != null) {
                            LocationPuck(
                                idPrefix = "user-location",
                                locationState = userLocationState,
                                cameraState = cameraState,
                            )
                        }

                        MapObjectsRendererAdapter(
                            objects = state.visibleObjects,
                            onObjectTapped = { objectId ->
                                dispatch(MapIntent.ObjectTapped(objectId))
                            },
                        )
                    },
                )
            }
        }

        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            onClick = {
                dispatch(MapIntent.RecenterClicked)
            },
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Recenter",
            )
        }
    }
}

private const val CAMERA_SETTLE_DEBOUNCE_MS = 300L

private fun areCameraPositionsEquivalent(first: CameraPosition, second: CameraPosition): Boolean {
    return (first.target.latitude - second.target.latitude).absoluteValue < CAMERA_SETTLE_COORDINATE_THRESHOLD &&
        (first.target.longitude - second.target.longitude).absoluteValue < CAMERA_SETTLE_COORDINATE_THRESHOLD &&
        (first.zoom - second.zoom).absoluteValue < CAMERA_SETTLE_ZOOM_THRESHOLD
}

private const val CAMERA_SETTLE_COORDINATE_THRESHOLD = 0.0001
private const val CAMERA_SETTLE_ZOOM_THRESHOLD = 0.01
