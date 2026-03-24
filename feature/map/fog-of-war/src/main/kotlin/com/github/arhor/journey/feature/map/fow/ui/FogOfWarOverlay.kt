package com.github.arhor.journey.feature.map.fow.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderData
import com.github.arhor.journey.feature.map.fow.model.FogOfWarUiState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.util.MaplibreComposable

internal const val ACTIVE_FOG_OF_WAR_SOURCE_ID = "fog-of-war-source-active"
internal const val ACTIVE_FOG_OF_WAR_LAYER_ID = "fog-of-war-layer-active"
internal const val HANDOFF_FOG_OF_WAR_SOURCE_ID = "fog-of-war-source-handoff"
internal const val HANDOFF_FOG_OF_WAR_LAYER_ID = "fog-of-war-layer-handoff"

internal val EMPTY_FOG_GEO_JSON_DATA = GeoJsonData.JsonString(
    """{"type":"FeatureCollection","features":[]}""",
)

@Composable
@MaplibreComposable
fun FogOfWarOverlay(
    state: FogOfWarUiState,
) {
    if (!state.isOverlayEnabled) {
        return
    }

    for (spec in state.fogOfWarLayerSpecs()) {
        FogOfWarRendererAdapter(
            fogRenderData = spec.renderData,
            sourceId = spec.sourceId,
            layerId = spec.layerId,
            isVisible = spec.isVisible,
        )
    }
}

@Composable
@MaplibreComposable
internal fun FogOfWarRendererAdapter(
    fogRenderData: FogOfWarRenderData?,
    sourceId: String,
    layerId: String,
    isVisible: Boolean,
) {
    val geoJsonData = fogRenderData?.geoJsonData ?: EMPTY_FOG_GEO_JSON_DATA
    val source = remember(sourceId) { GeoJsonSource(id = sourceId, data = geoJsonData, options = GeoJsonOptions()) }

    LaunchedEffect(source, geoJsonData) {
        source.setData(geoJsonData)
    }

    FillLayer(
        id = layerId,
        source = source,
        color = const(Color(0xFF000000)),
        opacity = const(0.90f),
        visible = isVisible,
    )
}

internal fun FogOfWarUiState.fogOfWarLayerSpecs(): List<FogOfWarLayerSpec> = listOf(
    FogOfWarLayerSpec(
        renderData = handoffRenderData,
        sourceId = HANDOFF_FOG_OF_WAR_SOURCE_ID,
        layerId = HANDOFF_FOG_OF_WAR_LAYER_ID,
        isVisible = handoffRenderData != null,
    ),
    FogOfWarLayerSpec(
        renderData = activeRenderData,
        sourceId = ACTIVE_FOG_OF_WAR_SOURCE_ID,
        layerId = ACTIVE_FOG_OF_WAR_LAYER_ID,
        isVisible = activeRenderData != null,
    ),
)

internal data class FogOfWarLayerSpec(
    val renderData: FogOfWarRenderData?,
    val sourceId: String,
    val layerId: String,
    val isVisible: Boolean,
)
