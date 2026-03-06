package com.github.arhor.journey.ui.views.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.arhor.journey.R
import com.github.arhor.journey.ui.LocalSnackbarHostState
import com.github.arhor.journey.ui.views.map.renderer.MapObjectsRendererAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.rememberStyleState
import org.maplibre.spatialk.geojson.Position
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

@Composable
fun MapRoute(
    vm: MapViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.effects.collectLatest { effect ->
            when (effect) {
                is MapEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                MapEffect.RequestLocationPermission -> {
                    snackbarHostState.showSnackbar("Location permission is not implemented yet.")
                }
                is MapEffect.OpenObjectDetails -> snackbarHostState.showSnackbar("Open details for ${effect.objectId}")
            }
        }
    }

    MapScreen(
        state = state,
        dispatch = vm::dispatch,
    )
}

@Composable
fun MapScreen(
    state: MapUiState,
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
    var isMapLoaded by remember { mutableStateOf(false) }

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
                    MapIntent.OnCameraSettled(
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

    LaunchedEffect(state.styleLoadErrorMessage) {
        if (state.styleLoadErrorMessage == null) {
            isMapLoaded = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        key(state.styleReloadToken) {
            MaplibreMap(
                modifier = Modifier.fillMaxSize(),
                baseStyle = state.resolvedStyle.toBaseStyle(),
                cameraState = cameraState,
                styleState = styleState,
                options = MapOptions(
                    gestureOptions = GestureOptions.Standard,
                    ornamentOptions = OrnamentOptions(
                        isAttributionEnabled = state.isAttributionVisible,
                    ),
                ),
                onMapClick = { position, _ ->
                    dispatch(
                        MapIntent.OnMapTapped(
                            target = LatLng(
                                latitude = position.latitude,
                                longitude = position.longitude,
                            ),
                        ),
                    )
                    org.maplibre.compose.util.ClickResult.Pass
                },
                onMapLoadFinished = {
                    isMapLoaded = true
                },
                onMapLoadFailed = { error ->
                    isMapLoaded = false
                    dispatch(MapIntent.OnMapLoadFailed(error))
                },
                content = {
                    MapObjectsRendererAdapter(
                        objects = state.visibleObjects,
                        onObjectTapped = { objectId ->
                            dispatch(MapIntent.OnObjectTapped(objectId))
                        },
                    )
                },
            )
        }

        if ((state.isLoading || !isMapLoaded) && state.styleLoadErrorMessage == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        state.styleLoadErrorMessage?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { dispatch(MapIntent.RetryStyleLoad) }) {
                    Text(text = stringResource(R.string.common_retry))
                }
            }
        }
    }
}

private fun MapResolvedStyle.toBaseStyle(): BaseStyle = when (this) {
    is MapResolvedStyle.Json -> BaseStyle.Json(value)
    is MapResolvedStyle.Uri -> BaseStyle.Uri(value)
}

private const val CAMERA_SETTLE_DEBOUNCE_MS = 300L

private fun areCameraPositionsEquivalent(first: CameraPosition, second: CameraPosition): Boolean {
    return (first.target.latitude - second.target.latitude).absoluteValue < CAMERA_SETTLE_COORDINATE_THRESHOLD &&
        (first.target.longitude - second.target.longitude).absoluteValue < CAMERA_SETTLE_COORDINATE_THRESHOLD &&
        (first.zoom - second.zoom).absoluteValue < CAMERA_SETTLE_ZOOM_THRESHOLD
}

private const val CAMERA_SETTLE_COORDINATE_THRESHOLD = 0.0001
private const val CAMERA_SETTLE_ZOOM_THRESHOLD = 0.01
