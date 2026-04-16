package com.github.arhor.journey.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.core.ui.components.ErrorMessage
import com.github.arhor.journey.core.ui.components.LoadingIndicator
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.feature.map.camera.MapCameraCoordinator
import com.github.arhor.journey.feature.map.camera.MapCameraGestureReporter
import com.github.arhor.journey.feature.map.camera.MapCameraSettledReporter
import com.github.arhor.journey.feature.map.camera.MapViewportReporter
import com.github.arhor.journey.feature.map.camera.recenterTargetLocation
import com.github.arhor.journey.feature.map.camera.resolveInitialMapCameraPosition
import com.github.arhor.journey.feature.map.components.ResetCameraButton
import com.github.arhor.journey.feature.map.fow.ui.FogOfWarOverlay
import com.github.arhor.journey.feature.map.gesture.mapRotationGestureHandler
import com.github.arhor.journey.feature.map.location.USER_LOCATION_PUCK_ID_PREFIX
import com.github.arhor.journey.feature.map.location.rememberMapLocationProvider
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.MapViewportSize
import com.github.arhor.journey.feature.map.renderer.MapObjectsRendererAdapter
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.map.RenderOptions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.rememberStyleState

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
    val userLocationProvider = rememberMapLocationProvider(state.currentLocation)
    val userLocationState = rememberUserLocationState(userLocationProvider)
    val cameraState = key(state.cameraPosition == null) {
        rememberCameraState(
            firstPosition = resolveInitialMapCameraPosition(state.cameraPosition),
        )
    }
    val styleState = rememberStyleState()
    val currentUserLocation = userLocationState.location
    val onObjectTapped = remember(dispatch) {
        { objectId: String ->
            dispatch(MapIntent.ObjectTapped(objectId))
        }
    }
    val onCameraGestureStarted = remember(dispatch) {
        { position: CameraPositionState ->
            dispatch(MapIntent.CameraGestureStarted(position))
        }
    }
    val onCameraSettled = remember(dispatch) {
        { position: CameraPositionState, origin: CameraUpdateOrigin ->
            dispatch(MapIntent.CameraSettled(position = position, origin = origin))
        }
    }

    MapCameraCoordinator(
        cameraState = cameraState,
        target = state.cameraPosition,
        origin = state.cameraUpdateOrigin,
        northResetRequestToken = state.northResetRequestToken,
        recenterTargetLocation = state.recenterTargetLocation(),
        onCurrentLocationUnavailable = { dispatch(MapIntent.CurrentLocationUnavailable) },
    )
    MapCameraGestureReporter(
        cameraState = cameraState,
        restartKey = state.cameraPosition,
        onGestureStarted = onCameraGestureStarted,
    )
    MapViewportReporter(
        cameraState = cameraState,
        restartKey = state.cameraPosition,
        onViewportChanged = { visibleBounds ->
            dispatch(MapIntent.CameraViewportChanged(visibleBounds = visibleBounds))
        },
    )
    MapCameraSettledReporter(
        cameraState = cameraState,
        restartKey = state.cameraPosition,
        onCameraSettled = onCameraSettled,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(MAP_ROTATION_OVERLAY_TEST_TAG)
            .mapRotationGestureHandler(
                cameraState = cameraState,
                onGestureStarted = onCameraGestureStarted,
                onCameraSettled = onCameraSettled,
            ),
    ) {
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
                        renderOptions = RenderOptions.Standard,
                        gestureOptions = GestureOptions(
                            isRotateEnabled = false,
                            isScrollEnabled = false,
                            isTiltEnabled = false,
                            isZoomEnabled = true,
                            isDoubleTapEnabled = true,
                            isQuickZoomEnabled = true,
                        ),
                        ornamentOptions = OrnamentOptions.AllDisabled,
                    ),
                    onMapClick = { _, _ ->
                        dispatch(MapIntent.MapTapped)
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

                    FogOfWarOverlay(state = state.fogOfWar.toRenderState())

                    MapObjectsRendererAdapter(
                        objects = state.visibleObjects,
                        onObjectTapped = onObjectTapped,
                    )
                }
            }
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

        MapPlayerHud(
            state = hudState,
            onHeroClick = onOpenHero,
            onSettingsClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )

        ResetCameraButton(
            onClick = { dispatch(MapIntent.RecenterClicked) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(96.dp)
                .padding(end = 16.dp, bottom = 16.dp),
        )
    }

    state.selectedWatchtower?.let { selectedWatchtower ->
        WatchtowerBottomSheet(
            state = selectedWatchtower,
            onDismiss = { dispatch(MapIntent.DismissWatchtowerSheet) },
            onClaim = { dispatch(MapIntent.ClaimSelectedWatchtower) },
            onUpgrade = { dispatch(MapIntent.UpgradeSelectedWatchtower) },
        )
    }
}

internal const val MAP_ROTATION_OVERLAY_TEST_TAG = "map_rotation_overlay"

private val CAMERA_ZOOM_BOUNDS = 14f..20f
