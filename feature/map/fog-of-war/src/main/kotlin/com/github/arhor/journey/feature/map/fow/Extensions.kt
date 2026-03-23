package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.feature.map.fow.model.TileRegionGeometry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Polygon

internal fun List<TileRegionGeometry>.toPolygonFeatureCollection(
    checkCancelled: () -> Unit = {},
): FeatureCollection<Polygon, JsonObject?> =
    FeatureCollection(
        features = mapIndexed { index, region ->
            checkCancelled()
            Feature(
                geometry = region.toPolygon(),
                properties = buildJsonObject { },
                id = JsonPrimitive("${region.zoom}#$index"),
            )
        },
    )
