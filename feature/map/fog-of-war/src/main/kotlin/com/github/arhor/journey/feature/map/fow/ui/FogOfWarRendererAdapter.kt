package com.github.arhor.journey.feature.map.fow.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderData
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.util.MaplibreComposable

@Composable
@MaplibreComposable
internal fun FogOfWarRendererAdapter(
    fogRenderData: FogOfWarRenderData?,
) {
    val geoJsonData = fogRenderData?.geoJsonData ?: return
    val source = remember { GeoJsonSource(id = FOG_OF_WAR_SOURCE_ID, data = geoJsonData, options = GeoJsonOptions()) }

    LaunchedEffect(source, geoJsonData) {
        source.setData(geoJsonData)
    }

    FillLayer(
        id = FOG_OF_WAR_LAYER_ID,
        source = source,
        color = const(Color(0xFF000000)),
        opacity = const(0.90f),
    )
}

internal const val FOG_OF_WAR_SOURCE_ID = "fog-of-war-source"
internal const val FOG_OF_WAR_LAYER_ID = "fog-of-war-layer"
