package com.github.arhor.journey.feature.map.fow.model

import androidx.compose.runtime.Immutable
import org.maplibre.spatialk.geojson.FeatureCollection

/**
 * Render-ready fog overlay data prepared off the composable path.
 */
@Immutable
data class FogOfWarRenderData(
    val featureCollection: FeatureCollection<*, *>,
)
