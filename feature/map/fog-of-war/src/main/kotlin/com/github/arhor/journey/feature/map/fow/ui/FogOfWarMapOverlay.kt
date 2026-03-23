package com.github.arhor.journey.feature.map.fow.ui

import androidx.compose.runtime.Composable
import com.github.arhor.journey.feature.map.fow.model.FogOfWarUiState
import org.maplibre.compose.util.MaplibreComposable

@Composable
@MaplibreComposable
fun ApplyFogOfWar(
    state: FogOfWarUiState,
) {
    if (!state.isOverlayEnabled) {
        return
    }

    FogOfWarRendererAdapter(
        fogRenderData = state.renderData,
    )
}
