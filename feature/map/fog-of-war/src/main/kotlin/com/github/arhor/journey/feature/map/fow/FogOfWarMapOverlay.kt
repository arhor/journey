package com.github.arhor.journey.feature.map.fow

import androidx.compose.runtime.Composable
import org.maplibre.compose.util.MaplibreComposable

@Composable
@MaplibreComposable
fun ApplyFogOfWar(
    state: FogOfWarUiState,
    onSourceDataUpdated: (Long) -> Unit = {},
) {
    if (!state.isOverlayEnabled) {
        return
    }

    FogOfWarRendererAdapter(
        fogRenderData = state.renderData,
        onSourceDataUpdated = onSourceDataUpdated,
    )
}
