package com.github.arhor.journey.feature.map.viewinterop

import android.graphics.PointF
import com.github.arhor.journey.feature.map.model.MapObjectUiModel
import com.google.gson.JsonObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

internal const val MAP_OBJECT_SOURCE_ID = "journey-map-objects-source"
internal const val BREACH_NODE_CIRCLE_LAYER_ID = "journey-breach-node-circle-layer"
internal const val MAP_OBJECT_ID_PROPERTY = "objectId"

internal class MapObjectLayerController {
    private var style: Style? = null

    fun attach(style: Style) {
        this.style = style
        ensureLayer(style)
    }

    fun update(objects: List<MapObjectUiModel>) {
        style
            ?.getSourceAs<GeoJsonSource>(MAP_OBJECT_SOURCE_ID)
            ?.setGeoJson(toFeatureCollection(objects))
    }

    fun queryObjectIdAt(
        map: MapLibreMap,
        screenPoint: PointF,
    ): String? =
        map.queryRenderedFeatures(screenPoint, BREACH_NODE_CIRCLE_LAYER_ID)
            .firstOrNull()
            ?.takeIf { it.hasNonNullValueForProperty(MAP_OBJECT_ID_PROPERTY) }
            ?.getStringProperty(MAP_OBJECT_ID_PROPERTY)

    fun cleanup() {
        style?.removeLayer(BREACH_NODE_CIRCLE_LAYER_ID)
        style?.removeSource(MAP_OBJECT_SOURCE_ID)
        style = null
    }

    internal fun toFeatureCollection(objects: List<MapObjectUiModel>): FeatureCollection =
        FeatureCollection.fromFeatures(
            objects
                .sortedBy(MapObjectUiModel::id)
                .map { mapObject ->
                    Feature.fromGeometry(
                        Point.fromLngLat(
                            mapObject.position.longitude,
                            mapObject.position.latitude,
                        ),
                        JsonObject().apply {
                            addProperty(MAP_OBJECT_ID_PROPERTY, mapObject.id)
                            addProperty("kind", mapObject.kind.name)
                            addProperty("title", mapObject.title)
                            mapObject.description?.let { description ->
                                addProperty("description", description)
                            }
                            mapObject.markerState?.let { markerState ->
                                addProperty("markerState", markerState.name)
                            }
                        },
                    )
                },
        )

    private fun ensureLayer(style: Style) {
        if (style.getSourceAs<GeoJsonSource>(MAP_OBJECT_SOURCE_ID) == null) {
            style.addSource(
                GeoJsonSource(
                    MAP_OBJECT_SOURCE_ID,
                    FeatureCollection.fromFeatures(emptyArray()),
                ),
            )
        }

        if (style.getLayer(BREACH_NODE_CIRCLE_LAYER_ID) == null) {
            style.addLayer(
                CircleLayer(BREACH_NODE_CIRCLE_LAYER_ID, MAP_OBJECT_SOURCE_ID).withProperties(
                    circleColor("#38BDF8"),
                    circleRadius(8f),
                    circleOpacity(0.92f),
                    circleStrokeColor("#082F49"),
                    circleStrokeWidth(1.5f),
                    circleStrokeOpacity(1f),
                ),
            )
        }
    }
}
