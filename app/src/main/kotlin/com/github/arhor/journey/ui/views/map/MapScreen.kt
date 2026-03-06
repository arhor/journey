package com.github.arhor.journey.ui.views.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.arhor.journey.ui.LocalSnackbarHostState
import kotlinx.coroutines.flow.collectLatest
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.rememberStyleState
import org.maplibre.spatialk.geojson.Position

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
                latitude = state.cameraTarget.latitude,
                longitude = state.cameraTarget.longitude,
            ),
            zoom = state.zoom,
        ),
    )
    val styleState = rememberStyleState()
    var isMapLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(state.cameraTarget, state.zoom) {
        val current = cameraState.position
        if (
            current.target.latitude != state.cameraTarget.latitude ||
            current.target.longitude != state.cameraTarget.longitude ||
            current.zoom != state.zoom
        ) {
            cameraState.position = current.copy(
                target = Position(
                    latitude = state.cameraTarget.latitude,
                    longitude = state.cameraTarget.longitude,
                ),
                zoom = state.zoom,
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            baseStyle = BaseStyle.Uri(state.styleUri),
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
                dispatch(
                    MapIntent.OnMapLoaded(
                        cameraTarget = LatLng(
                            latitude = cameraState.position.target.latitude,
                            longitude = cameraState.position.target.longitude,
                        ),
                        zoom = cameraState.position.zoom,
                    ),
                )
            },
            onMapLoadFailed = {
                isMapLoaded = false
            },
        )

        if (state.isLoading || !isMapLoaded) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
