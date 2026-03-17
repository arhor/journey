package com.github.arhor.journey.feature.map.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileGrid
import com.github.arhor.journey.domain.model.ExplorationTileRange
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.Position

@Composable
@MaplibreComposable
fun TilesGridRendererAdapter(
    tileRange: ExplorationTileRange?,
) {
    val geoJsonData = remember(tileRange) { tileRange.toTileGridGeoJsonDataOrNull() } ?: return
    val source = remember {
        GeoJsonSource(
            id = TILES_GRID_SOURCE_ID,
            data = geoJsonData,
            options = org.maplibre.compose.sources.GeoJsonOptions(),
        )
    }

    LaunchedEffect(source, geoJsonData) {
        source.setData(geoJsonData)
    }

    LineLayer(
        id = TILES_GRID_LAYER_ID,
        source = source,
        color = const(Color(0xFFFFFFFF)),
        opacity = const(0.65f),
        width = const(1.dp),
    )
}

internal fun ExplorationTileRange.toTileGridFeatureCollection(): FeatureCollection<Polygon, JsonObject?> =
    FeatureCollection(
        features = asSequence().mapIndexed { index, tile ->
            Feature(
                geometry = tile.toGridPolygon(),
                properties = buildJsonObject { },
                id = JsonPrimitive("${tile.zoom}#$index"),
            )
        }.toList(),
    )

internal fun ExplorationTileRange?.toTileGridGeoJsonDataOrNull(): GeoJsonData.Features? =
    this
        ?.takeIf { it.tileCount > 0 }
        ?.toTileGridFeatureCollection()
        ?.let(GeoJsonData::Features)

private fun ExplorationTile.toGridPolygon(): Polygon {
    val bounds = ExplorationTileGrid.bounds(this)

    return Polygon(
        coordinates = listOf(
            listOf(
                Position(longitude = bounds.west, latitude = bounds.north),
                Position(longitude = bounds.east, latitude = bounds.north),
                Position(longitude = bounds.east, latitude = bounds.south),
                Position(longitude = bounds.west, latitude = bounds.south),
                Position(longitude = bounds.west, latitude = bounds.north),
            ),
        ),
    )
}

internal const val TILES_GRID_SOURCE_ID = "tiles-grid-source"
internal const val TILES_GRID_LAYER_ID = "tiles-grid-layer"
