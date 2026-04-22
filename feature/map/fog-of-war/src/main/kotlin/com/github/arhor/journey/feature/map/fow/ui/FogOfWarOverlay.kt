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
import com.github.arhor.journey.feature.map.fow.model.fogOfWarLayerSpecs
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.util.MaplibreComposable

internal val EMPTY_FOG_GEO_JSON_DATA = GeoJsonData.JsonString(
    """{"type":"FeatureCollection","features":[]}""",
)

@Composable
@MaplibreComposable
fun FogOfWarOverlay(
    state: FogOfWarRenderState,
) {
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
