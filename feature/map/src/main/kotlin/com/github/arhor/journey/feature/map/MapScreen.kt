package com.github.arhor.journey.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.core.ui.components.ErrorMessage
import com.github.arhor.journey.core.ui.components.LoadingIndicator
import com.github.arhor.journey.feature.map.model.MapViewportSize
import com.github.arhor.journey.feature.map.viewinterop.MapLibreViewMapScreen

internal const val MAP_STARTUP_SPLASH_TEST_TAG = "map_startup_splash"

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
    mapContent: @Composable (Modifier, (MapIntent) -> Unit) -> Unit = { modifier, mapDispatch ->
        MapLibreViewMapScreen(
            modifier = modifier,
            fogOfWar = state.fogOfWar.toRenderState(),
            onViewportChanged = { visibleBounds ->
                mapDispatch(MapIntent.CameraViewportChanged(visibleBounds))
            },
            onCameraGestureStarted = { cameraPosition ->
                mapDispatch(MapIntent.CameraGestureStarted(cameraPosition))
            },
            onCameraSettled = { cameraPosition, origin ->
                mapDispatch(
                    MapIntent.CameraSettled(
                        position = cameraPosition,
                        origin = origin,
                    ),
                )
            },
            onLocationPermissionGranted = {
                mapDispatch(MapIntent.LocationPermissionResult(isGranted = true))
            },
            onMapLoadFailed = { message ->
                mapDispatch(MapIntent.MapLoadFailed(message))
            },
            onMapSurfaceSessionStarted = { sessionId ->
                mapDispatch(MapIntent.MapSurfaceSessionStarted(sessionId))
            },
            onFirstLocationFix = { sessionId ->
                mapDispatch(MapIntent.FirstLocationFixAcquired(sessionId))
            },
            onFirstMapFrameRendered = { sessionId ->
                mapDispatch(MapIntent.FirstMapFrameRendered(sessionId))
            },
            onStartupTimeout = { sessionId ->
                mapDispatch(MapIntent.StartupGateTimeoutElapsed(sessionId))
            },
        )
    },
) {
    Box(modifier = Modifier.fillMaxSize()) {
        mapContent(
            Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    dispatch(
                        MapIntent.MapViewportSizeChanged(
                            MapViewportSize(
                                widthPx = size.width,
                                heightPx = size.height,
                            ),
                        ),
                    )
                },
            dispatch,
        )

        MapPlayerHud(
            state = hudState,
            onHeroClick = onOpenHero,
            onSettingsClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )

        if (state.isStartupSplashVisible) {
            StartupSplashOverlay(
                message = stringResource(state.startupSplashMessage),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun StartupSplashOverlay(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .testTag(MAP_STARTUP_SPLASH_TEST_TAG)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            ),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
                Text(
                    modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp),
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
