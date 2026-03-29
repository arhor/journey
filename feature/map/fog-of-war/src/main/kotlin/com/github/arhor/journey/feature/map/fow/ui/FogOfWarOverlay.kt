package com.github.arhor.journey.feature.map.fow.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderData
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderState
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
internal const val HIDDEN_EXPLORED_SOURCE_ID = "fog-of-war-source-hidden-explored"
internal const val HIDDEN_EXPLORED_LAYER_ID = "fog-of-war-layer-hidden-explored"
internal const val ACTIVE_FOG_OF_WAR_OPACITY = 0.90f
internal const val HIDDEN_EXPLORED_OPACITY = 0.40f

internal val EMPTY_FOG_GEO_JSON_DATA = GeoJsonData.JsonString(
    """{"type":"FeatureCollection","features":[]}""",
)

@Composable
@MaplibreComposable
fun FogOfWarOverlay(
    state: FogOfWarRenderState,
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
            opacity = spec.opacity,
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
    opacity: Float,
) {
    val source = remember(sourceId) {
        GeoJsonSource(
            id = sourceId,
            data = EMPTY_FOG_GEO_JSON_DATA,
            options = GeoJsonOptions(),
        )
    }
    var lastAppliedGeoJsonData by remember(sourceId) {
        mutableStateOf<GeoJsonData.Features?>(null)
    }

    SideEffect {
        val nextGeoJsonData = fogRenderData?.geoJsonData
        if (nextGeoJsonData != null && nextGeoJsonData !== lastAppliedGeoJsonData) {
            source.setData(nextGeoJsonData)
            lastAppliedGeoJsonData = nextGeoJsonData
        }
    }

    FillLayer(
        id = layerId,
        source = source,
        color = const(Color(0xFF000000)),
        opacity = const(opacity),
        visible = isVisible,
    )
}

internal fun FogOfWarRenderState.fogOfWarLayerSpecs(): List<FogOfWarLayerSpec> = listOf(
    FogOfWarLayerSpec(
        renderData = hiddenExploredRenderData,
        sourceId = HIDDEN_EXPLORED_SOURCE_ID,
        layerId = HIDDEN_EXPLORED_LAYER_ID,
        isVisible = hiddenExploredRenderData != null,
        opacity = HIDDEN_EXPLORED_OPACITY,
    ),
    FogOfWarLayerSpec(
        renderData = handoffRenderData,
        sourceId = HANDOFF_FOG_OF_WAR_SOURCE_ID,
        layerId = HANDOFF_FOG_OF_WAR_LAYER_ID,
        isVisible = handoffRenderData != null,
        opacity = ACTIVE_FOG_OF_WAR_OPACITY,
    ),
    FogOfWarLayerSpec(
        renderData = activeRenderData,
        sourceId = ACTIVE_FOG_OF_WAR_SOURCE_ID,
        layerId = ACTIVE_FOG_OF_WAR_LAYER_ID,
        isVisible = activeRenderData != null,
        opacity = ACTIVE_FOG_OF_WAR_OPACITY,
    ),
)

internal data class FogOfWarLayerSpec(
    val renderData: FogOfWarRenderData?,
    val sourceId: String,
    val layerId: String,
    val isVisible: Boolean,
    val opacity: Float,
)
