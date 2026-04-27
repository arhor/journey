package com.github.arhor.journey.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.core.ui.components.ErrorMessage
import com.github.arhor.journey.core.ui.components.LoadingIndicator
import com.github.arhor.journey.feature.map.model.MapViewportSize
import com.github.arhor.journey.feature.map.viewinterop.DEFAULT_VIEW_MAP_STYLE_URL
import com.github.arhor.journey.feature.map.viewinterop.MapLibreViewMapScreen

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
    val resolvedStyle = state.selectedStyle?.value ?: DEFAULT_VIEW_MAP_STYLE_URL
    var launchRequestId by remember { mutableLongStateOf(0L) }
    var rippleLaunchRequest by remember { mutableStateOf<RippleLaunchRequest?>(null) }
    var rippleOrigin by remember { mutableStateOf<Offset?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        MapLibreViewMapScreen(
            modifier = Modifier
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
            styleUrl = resolvedStyle,
            fogOfWar = state.fogOfWar.toRenderState(),
            onViewportChanged = { visibleBounds ->
                dispatch(MapIntent.CameraViewportChanged(visibleBounds))
            },
            onLocationPermissionGranted = {
                dispatch(MapIntent.LocationPermissionResult(isGranted = true))
            },
            onMapLoadFailed = { message ->
                dispatch(MapIntent.MapLoadFailed(message))
            },
            onUserLocationScreenPointChanged = { rippleOrigin = it },
        )
        RippleGridOverlay(
            launchRequest = rippleLaunchRequest,
            waveOrigin = rippleOrigin,
            modifier = Modifier.fillMaxSize(),
        )

        MapLocatorSkillButton(
            onClick = {
                launchRequestId += 1L
                rippleLaunchRequest = RippleLaunchRequest(id = launchRequestId)
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        )

        MapPlayerHud(
            state = hudState,
            onHeroClick = onOpenHero,
            onSettingsClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
