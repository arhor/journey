package com.github.arhor.journey.ui.views.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.R
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.UserLocationState
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberNullLocationProvider
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.rememberStyleState
import org.maplibre.spatialk.geojson.Position
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


@Composable
fun MapScreen(
    state: MapUiState,
    dispatch: (MapIntent) -> Unit,
) {
    when (state) {
        is MapUiState.Loading -> LoadingIndicator()
        is MapUiState.Failure -> ErrorMessage(message = state.errorMessage)
        is MapUiState.Content -> MapContent(
            state = state,
            dispatch = dispatch,
        )
    }
}

@Composable
internal fun MapContent(
    state: MapUiState.Content,
    dispatch: (MapIntent) -> Unit,
) {
    val context = LocalContext.current
    val userLocationState = rememberUserLocationStateInternal(context)
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
    val currentUserLocation = userLocationState.location
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            dispatch(
                MapIntent.LocationPermissionResult(
                    isGranted = it.hasGrantedLocationPermission() || context.checkPermission()
                )
            )
        },
    )

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

    LaunchedEffect(state.recenterRequestToken) {
        if (state.recenterRequestToken <= 0) {
            return@LaunchedEffect
        }

        val location = userLocationState.location ?: withTimeoutOrNull(USER_LOCATION_TIMEOUT) {
            snapshotFlow { userLocationState.location }
                .filterNotNull()
                .first()
        }

        if (location == null) {
            dispatch(MapIntent.CurrentLocationUnavailable)
            return@LaunchedEffect
        }

        cameraState.animateTo(
            finalPosition = cameraState.position.copy(target = location.position),
            duration = USER_LOCATION_RECENTER_ANIMATION_DURATION,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        state.selectedStyle?.let { style ->
            key(style) {
                MaplibreMap(
                    modifier = Modifier.fillMaxSize(),
                    baseStyle = when (style.type) {
                        MapStyle.Type.BUNDLE -> BaseStyle.Json(style.value)
                        MapStyle.Type.REMOTE -> BaseStyle.Uri(style.value)
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
                    onMapLoadFailed = { dispatch(MapIntent.MapLoadFailed(it)) },
                ) {
                    if (currentUserLocation != null) {
                        LocationPuck(
                            idPrefix = USER_LOCATION_PUCK_ID_PREFIX,
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
                }
            }
        }

        FloatingActionButton(
            onClick = {
                dispatch(MapIntent.RecenterClicked)
                locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    PaddingValues(
                        horizontal = 16.dp,
                        vertical = 24.dp,
                    ),
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = stringResource(R.string.map_recenter_content_description),
            )
        }
    }
}

private const val CAMERA_SETTLE_DEBOUNCE_MS = 300L
private const val USER_LOCATION_PUCK_ID_PREFIX = "user-location"
private const val CAMERA_SETTLE_COORDINATE_THRESHOLD = 0.0001
private const val CAMERA_SETTLE_ZOOM_THRESHOLD = 0.01
private val USER_LOCATION_TIMEOUT = 5.seconds
private val USER_LOCATION_RECENTER_ANIMATION_DURATION = 600.milliseconds
private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
private fun rememberUserLocationStateInternal(ctx: Context): UserLocationState {
    return rememberUserLocationState(
        if (ctx.checkPermission()) {
            rememberDefaultLocationProvider()
        } else {
            rememberNullLocationProvider()
        }
    )
}

private fun areCameraPositionsEquivalent(a: CameraPosition, b: CameraPosition): Boolean {
    return abs(a.target.latitude - b.target.latitude) < CAMERA_SETTLE_COORDINATE_THRESHOLD
        && abs(a.target.longitude - b.target.longitude) < CAMERA_SETTLE_COORDINATE_THRESHOLD
        && abs(a.zoom - b.zoom) < CAMERA_SETTLE_ZOOM_THRESHOLD
}

private fun Context.checkPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED


private fun Context.checkPermission(): Boolean {
    return checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        || checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
}

private fun Map<String, Boolean>.hasGrantedLocationPermission(): Boolean {
    return LOCATION_PERMISSIONS.any { this[it] == true }
}
