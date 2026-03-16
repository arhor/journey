package com.github.arhor.journey.feature.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.renderer.FogOfWarRendererAdapter
import com.github.arhor.journey.feature.map.renderer.MapObjectsRendererAdapter
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
import org.maplibre.compose.map.RenderOptions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.rememberStyleState
import org.maplibre.spatialk.geojson.BoundingBox
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
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            dispatch(
                MapIntent.LocationPermissionResult(
                    isGranted = it || context.checkPermission()
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
        snapshotFlow {
            val projection = cameraState.projection

            CameraViewportSnapshot(
                position = cameraState.position,
                visibleBounds = projection?.queryVisibleBoundingBox()?.toGeoBounds(),
            )
        }.debounce(CAMERA_SETTLE_DEBOUNCE_MS)
            .distinctUntilChanged(::areCameraViewportSnapshotsEquivalent)
            .collectLatest { snapshot ->
                dispatch(
                    MapIntent.CameraSettled(
                        position = CameraPositionState(
                            target = LatLng(
                                latitude = snapshot.position.target.latitude,
                                longitude = snapshot.position.target.longitude,
                            ),
                            zoom = snapshot.position.zoom,
                        ),
                        origin = CameraUpdateOrigin.USER,
                        visibleBounds = snapshot.visibleBounds,
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
                        renderOptions = RenderOptions.Debug,
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

                    FogOfWarRendererAdapter(
                        fogRanges = state.fogOfWar.fogRanges,
                    )

                    MapObjectsRendererAdapter(
                        objects = state.visibleObjects,
                        onObjectTapped = { objectId ->
                            dispatch(MapIntent.ObjectTapped(objectId))
                        },
                    )
                }
            }
        }

        FogOfWarDebugPanel(
            fogOfWar = state.fogOfWar,
            onClearExploredTiles = {
                dispatch(MapIntent.ClearExploredTilesClicked)
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        )

        FloatingActionButton(
            onClick = {
                dispatch(MapIntent.RecenterClicked)
                locationPermissionLauncher.launch(LOCATION_PERMISSION)
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

@Composable
private fun FogOfWarDebugPanel(
    fogOfWar: FogOfWarUiState,
    onClearExploredTiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Fog z${fogOfWar.canonicalZoom}",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = "Visible tiles: ${fogOfWar.visibleTileCount}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Explored here: ${fogOfWar.exploredVisibleTileCount}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = if (fogOfWar.isSuppressedByVisibleTileLimit) {
                    "Fog hidden while zoomed out"
                } else {
                    "Fog regions: ${fogOfWar.fogRanges.size}"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = onClearExploredTiles,
            ) {
                Text(text = stringResource(R.string.map_clear_fog_button_label))
            }
        }
    }
}

private const val CAMERA_SETTLE_DEBOUNCE_MS = 300L
private const val USER_LOCATION_PUCK_ID_PREFIX = "user-location"
private const val CAMERA_SETTLE_COORDINATE_THRESHOLD = 0.0001
private const val CAMERA_SETTLE_ZOOM_THRESHOLD = 0.01
private const val CAMERA_SETTLE_BEARING_THRESHOLD = 0.1
private const val CAMERA_SETTLE_TILT_THRESHOLD = 0.1
private const val CAMERA_SETTLE_BOUNDS_THRESHOLD = 0.0001
private val USER_LOCATION_TIMEOUT = 5.seconds
private val USER_LOCATION_RECENTER_ANIMATION_DURATION = 600.milliseconds
private const val LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION

@SuppressLint("MissingPermission")
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

private fun areCameraViewportSnapshotsEquivalent(
    a: CameraViewportSnapshot,
    b: CameraViewportSnapshot,
): Boolean {
    return areCameraPositionsEquivalent(a.position, b.position)
        && areGeoBoundsEquivalent(a.visibleBounds, b.visibleBounds)
}

private fun areCameraPositionsEquivalent(a: CameraPosition, b: CameraPosition): Boolean {
    return abs(a.target.latitude - b.target.latitude) < CAMERA_SETTLE_COORDINATE_THRESHOLD
        && abs(a.target.longitude - b.target.longitude) < CAMERA_SETTLE_COORDINATE_THRESHOLD
        && abs(a.zoom - b.zoom) < CAMERA_SETTLE_ZOOM_THRESHOLD
        && abs(a.bearing - b.bearing) < CAMERA_SETTLE_BEARING_THRESHOLD
        && abs(a.tilt - b.tilt) < CAMERA_SETTLE_TILT_THRESHOLD
}

private fun areGeoBoundsEquivalent(a: GeoBounds?, b: GeoBounds?): Boolean {
    if (a == null || b == null) {
        return a == b
    }

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

private fun Context.checkPermission(permission: String = LOCATION_PERMISSION): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private data class CameraViewportSnapshot(
    val position: CameraPosition,
    val visibleBounds: GeoBounds?,
)
