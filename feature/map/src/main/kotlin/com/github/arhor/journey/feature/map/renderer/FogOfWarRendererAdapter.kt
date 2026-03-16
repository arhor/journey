package com.github.arhor.journey.feature.map.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.github.arhor.journey.domain.model.ExplorationTileGrid
import com.github.arhor.journey.domain.model.ExplorationTileRange
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.Position

@Composable
@MaplibreComposable
fun FogOfWarRendererAdapter(
    fogRanges: List<ExplorationTileRange>,
) {
    val geoJsonData = remember(fogRanges) { fogRanges.toGeoJsonDataOrNull() } ?: return
    val source = remember {
        GeoJsonSource(
            id = FOG_OF_WAR_SOURCE_ID,
            data = geoJsonData,
            options = org.maplibre.compose.sources.GeoJsonOptions(),
        )
    }

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

internal fun List<ExplorationTileRange>.toFeatureCollection(): FeatureCollection<Polygon, JsonObject?> =
    FeatureCollection(
        features = mapIndexed { index, range ->
            Feature(
                geometry = range.toPolygon(),
                properties = buildJsonObject { },
                id = JsonPrimitive("${range.zoom}/${range.minX}:${range.maxX}/${range.minY}:${range.maxY}#$index"),
            )
        },
    )

internal fun List<ExplorationTileRange>.toGeoJsonDataOrNull(): GeoJsonData.Features? =
    takeIf { it.isNotEmpty() }
        ?.toFeatureCollection()
        ?.let(GeoJsonData::Features)

private fun ExplorationTileRange.toPolygon(): Polygon {
    val bounds = ExplorationTileGrid.bounds(this)

    return Polygon(
        listOf(
            Position(longitude = bounds.west, latitude = bounds.north),
            Position(longitude = bounds.east, latitude = bounds.north),
            Position(longitude = bounds.east, latitude = bounds.south),
            Position(longitude = bounds.west, latitude = bounds.south),
            Position(longitude = bounds.west, latitude = bounds.north),
        ),
    )
}

internal const val FOG_OF_WAR_SOURCE_ID = "fog-of-war-source"
internal const val FOG_OF_WAR_LAYER_ID = "fog-of-war-layer"
