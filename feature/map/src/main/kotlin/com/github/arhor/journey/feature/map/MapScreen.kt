package com.github.arhor.journey.feature.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.core.ui.components.ErrorMessage
import com.github.arhor.journey.core.ui.components.LoadingIndicator
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.feature.map.fow.ApplyFogOfWar
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapViewportSize
import com.github.arhor.journey.feature.map.renderer.MapObjectsRendererAdapter
import com.github.arhor.journey.feature.map.renderer.TilesGridRendererAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.compose.camera.CameraMoveReason
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
    hudState: MapHudUiState,
    dispatch: (MapIntent) -> Unit,
    onOpenHero: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (state) {
        is MapUiState.Loading -> LoadingIndicator()
        is MapUiState.Failure -> ErrorMessage(message = state.errorMessage)
        is MapUiState.Content -> MapContent(
            state = state,
            hudState = hudState,
            dispatch = dispatch,
            onOpenHero = onOpenHero,
            onOpenSettings = onOpenSettings,
        )
    }
}

@Composable
internal fun MapContent(
    state: MapUiState.Content,
    hudState: MapHudUiState,
    dispatch: (MapIntent) -> Unit,
    onOpenHero: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val userLocationState = rememberUserLocationStateInternal(context)
    val cameraState = key(state.cameraPosition == null) {
        rememberCameraState(
            firstPosition = state.cameraPosition?.toCameraPosition() ?: CameraPosition(),
        )
    }
    val styleState = rememberStyleState()
    val currentUserLocation = userLocationState.location
    val latestUserLocation by rememberUpdatedState(state.userLocation)
    val onObjectTapped = remember(dispatch) {
        { objectId: String ->
            dispatch(MapIntent.ObjectTapped(objectId))
        }
    }

    LaunchedEffect(
        state.cameraPosition,
        state.cameraUpdateOrigin,
        cameraState.isCameraMoving,
        cameraState.moveReason,
    ) {
        val cameraPosition = state.cameraPosition ?: return@LaunchedEffect

        if (state.cameraUpdateOrigin != CameraUpdateOrigin.PROGRAMMATIC) {
            return@LaunchedEffect
        }

        if (cameraState.isCameraMoving && cameraState.moveReason == CameraMoveReason.GESTURE) {
            return@LaunchedEffect
        }

        val current = cameraState.position
        if (
            current.target.latitude != cameraPosition.target.latitude ||
            current.target.longitude != cameraPosition.target.longitude ||
            current.zoom != cameraPosition.zoom
        ) {
            cameraState.position = current.copy(
                target = Position(
                    latitude = cameraPosition.target.latitude,
                    longitude = cameraPosition.target.longitude,
                ),
                zoom = cameraPosition.zoom,
            )
        }
    }

    LaunchedEffect(state.cameraPosition, cameraState) {
        if (state.cameraPosition == null) {
            return@LaunchedEffect
        }

        snapshotFlow { cameraState.isCameraMoving to cameraState.moveReason }
            .distinctUntilChanged()
            .filter { (isCameraMoving, moveReason) ->
                isCameraMoving && moveReason == CameraMoveReason.GESTURE
            }
            .collectLatest {
                dispatch(
                    MapIntent.CameraGestureStarted(
                        position = cameraState.position.toCameraPositionState(),
                    ),
                )
            }
    }

    LaunchedEffect(state.cameraPosition, cameraState) {
        if (state.cameraPosition == null) {
            return@LaunchedEffect
        }

        snapshotFlow {
            cameraState.position
            cameraState.projection?.queryVisibleBoundingBox()?.toGeoBounds()
        }.filterNotNull()
            .distinctUntilChanged(::areGeoBoundsEquivalent)
            .collectLatest { visibleBounds ->
                dispatch(
                    MapIntent.CameraViewportChanged(
                        visibleBounds = visibleBounds,
                    ),
                )
            }
    }

    LaunchedEffect(state.cameraPosition, cameraState) {
        if (state.cameraPosition == null) {
            return@LaunchedEffect
        }

        snapshotFlow {
            CameraSettledSnapshot(
                position = cameraState.position,
                origin = cameraState.moveReason.toCameraUpdateOrigin(),
                isCameraMoving = cameraState.isCameraMoving,
            )
        }
            .debounce(CAMERA_SETTLE_DEBOUNCE_MS)
            .filter { !it.isCameraMoving }
            .distinctUntilChanged(::areCameraSettledSnapshotsEquivalent)
            .collectLatest { settled ->
                dispatch(
                    MapIntent.CameraSettled(
                        position = settled.position.toCameraPositionState(),
                        origin = settled.origin,
                    ),
                )
            }
    }

    LaunchedEffect(state.recenterRequestToken) {
        if (state.recenterRequestToken <= 0) {
            return@LaunchedEffect
        }

        val location = latestUserLocation ?: withTimeoutOrNull(USER_LOCATION_TIMEOUT) {
            snapshotFlow { latestUserLocation }
                .filterNotNull()
                .first()
        }

        if (location == null) {
            dispatch(MapIntent.CurrentLocationUnavailable)
            return@LaunchedEffect
        }

        cameraState.animateTo(
            finalPosition = cameraState.position.copy(
                target = Position(
                    latitude = location.latitude,
                    longitude = location.longitude,
                ),
                zoom = state.cameraPosition?.zoom ?: cameraState.position.zoom,
            ),
            duration = USER_LOCATION_RECENTER_ANIMATION_DURATION,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        state.selectedStyle?.let { style ->
            key(style) {
                MaplibreMap(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size ->
                            dispatch(
                                MapIntent.MapViewportSizeChanged(
                                    viewportSize = MapViewportSize(
                                        widthPx = size.width,
                                        heightPx = size.height,
                                    ),
                                ),
                            )
                        },
                    baseStyle = when (style.type) {
                        MapStyle.Type.BUNDLE -> BaseStyle.Json(style.value)
                        MapStyle.Type.REMOTE -> BaseStyle.Uri(style.value)
                    },
                    cameraState = cameraState,
                    zoomRange = CAMERA_ZOOM_BOUNDS,
                    styleState = styleState,
                    options = MapOptions(
                        renderOptions = state.debug.renderMode.toRenderOptions(),
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

                    ApplyFogOfWar(
                        state = state.fogOfWar,
                        onSourceDataUpdated = { elapsedMillis ->
                            if (BuildConfig.DEBUG) {
                                dispatch(MapIntent.FogOfWarSourceUpdated(elapsedMillis))
                            }
                        },
                    )

                    if (
                        state.debug.isTilesGridOverlayEnabled &&
                        !state.fogOfWar.isSuppressedByVisibleTileLimit
                    ) {
                        TilesGridRendererAdapter(
                            tileRange = state.fogOfWar.visibleTileRange,
                        )
                    }

                    MapObjectsRendererAdapter(
                        objects = state.visibleObjects,
                        onObjectTapped = onObjectTapped,
                    )
                }
            }
        }

        if (BuildConfig.DEBUG) {
            MapDebugInfoOverlay(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 110.dp, end = 16.dp),
            )

            FloatingActionButton(
                onClick = {
                    dispatch(MapIntent.DebugControlsClicked)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 88.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = stringResource(R.string.map_debug_button_content_description),
                )
            }
        }

        FloatingActionButton(
            onClick = {
                dispatch(MapIntent.AddPoiClicked)
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.map_add_poi_content_description),
            )
        }

        FloatingActionButton(
            onClick = {
                dispatch(MapIntent.RecenterClicked)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = stringResource(R.string.map_recenter_content_description),
            )
        }

        if (
            state.cameraPosition == null &&
            state.explorationTrackingStatus !in setOf(
                ExplorationTrackingStatus.PERMISSION_DENIED,
                ExplorationTrackingStatus.LOCATION_SERVICES_DISABLED,
            )
        ) {
            LoadingIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (BuildConfig.DEBUG && state.debug.isSheetVisible) {
            MapDebugControlsSheet(
                state = state,
                dispatch = dispatch,
            )
        }

        MapPlayerHud(
            state = hudState,
            onHeroClick = onOpenHero,
            onSettingsClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
        )
    }
}

private const val CAMERA_SETTLE_DEBOUNCE_MS = 100L
private const val USER_LOCATION_PUCK_ID_PREFIX = "user-location"
private const val CAMERA_SETTLE_COORDINATE_THRESHOLD = 0.0001
private const val CAMERA_SETTLE_ZOOM_THRESHOLD = 0.01
private const val CAMERA_SETTLE_BEARING_THRESHOLD = 0.1
private const val CAMERA_SETTLE_TILT_THRESHOLD = 0.1
private const val CAMERA_SETTLE_BOUNDS_THRESHOLD = 0.0001
private val USER_LOCATION_TIMEOUT = 5.seconds
private val USER_LOCATION_RECENTER_ANIMATION_DURATION = 600.milliseconds
private val CAMERA_ZOOM_BOUNDS = 14f..20f

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

private fun areCameraPositionsEquivalent(a: CameraPosition, b: CameraPosition): Boolean {
    return abs(a.target.latitude - b.target.latitude) < CAMERA_SETTLE_COORDINATE_THRESHOLD
        && abs(a.target.longitude - b.target.longitude) < CAMERA_SETTLE_COORDINATE_THRESHOLD
        && abs(a.zoom - b.zoom) < CAMERA_SETTLE_ZOOM_THRESHOLD
        && abs(a.bearing - b.bearing) < CAMERA_SETTLE_BEARING_THRESHOLD
        && abs(a.tilt - b.tilt) < CAMERA_SETTLE_TILT_THRESHOLD
}

private fun areCameraSettledSnapshotsEquivalent(
    a: CameraSettledSnapshot,
    b: CameraSettledSnapshot,
): Boolean {
    return a.origin == b.origin &&
        a.isCameraMoving == b.isCameraMoving &&
        areCameraPositionsEquivalent(a.position, b.position)
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

private fun Context.checkPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun CameraPosition.toCameraPositionState(): CameraPositionState =
    CameraPositionState(
        target = LatLng(
            latitude = target.latitude,
            longitude = target.longitude,
        ),
        zoom = zoom,
    )

private fun CameraPositionState.toCameraPosition(): CameraPosition = CameraPosition(
    target = Position(
        latitude = target.latitude,
        longitude = target.longitude,
    ),
    zoom = zoom,
)

private fun CameraMoveReason.toCameraUpdateOrigin(): CameraUpdateOrigin =
    if (this == CameraMoveReason.GESTURE) {
        CameraUpdateOrigin.USER
    } else {
        CameraUpdateOrigin.PROGRAMMATIC
    }

private data class CameraSettledSnapshot(
    val position: CameraPosition,
    val origin: CameraUpdateOrigin,
    val isCameraMoving: Boolean,
)

private fun MapRenderMode.toRenderOptions(): RenderOptions =
    when (this) {
        MapRenderMode.Standard -> RenderOptions.Standard
        MapRenderMode.Debug -> RenderOptions.Debug
    }
