package com.github.arhor.journey.feature.map.fow

import androidx.compose.runtime.Immutable
import org.maplibre.compose.sources.GeoJsonData

/**
 * Render-ready fog overlay data prepared off the composable path.
 */
@Immutable
data class FogOfWarRenderData(
    val geoJsonData: GeoJsonData.Features,
)
