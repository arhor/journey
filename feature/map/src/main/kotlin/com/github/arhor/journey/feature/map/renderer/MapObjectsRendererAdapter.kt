package com.github.arhor.journey.feature.map.renderer

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.feature.map.model.MapObjectKind
import com.github.arhor.journey.feature.map.model.MapObjectUiModel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.asBoolean
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.nil
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.step
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point

@Composable
@MaplibreComposable
fun MapObjectsRendererAdapter(
    objects: List<MapObjectUiModel>,
    onObjectTapped: (String) -> Unit,
) {
    val geoJsonData = remember(objects) { objects.toGeoJsonDataOrNull() } ?: return
    val source = remember {
        GeoJsonSource(
            id = GAME_ENTITIES_SOURCE_ID,
            data = geoJsonData,
            options = GeoJsonOptions(
                cluster = true,
                clusterRadius = CLUSTER_RADIUS,
                clusterMaxZoom = DECLUSTER_ZOOM,
            ),
        )
    }

    LaunchedEffect(source, geoJsonData) {
        source.setData(geoJsonData)
    }

    val woodPainter = rememberResourceTypePainter(ResourceType.WOOD)
    val coalPainter = rememberResourceTypePainter(ResourceType.COAL)
    val stonePainter = rememberResourceTypePainter(ResourceType.STONE)
    val resourceSpawnIcon = remember(woodPainter, coalPainter, stonePainter) {
        switch(
            input = feature[PROPERTY_OBJECT_RESOURCE_TYPE_ID].asString(const("")),
            case(
                label = ResourceType.WOOD.typeId,
                output = image(woodPainter, size = RESOURCE_ICON_SIZE),
            ),
            case(
                label = ResourceType.COAL.typeId,
                output = image(coalPainter, size = RESOURCE_ICON_SIZE),
            ),
            case(
                label = ResourceType.STONE.typeId,
                output = image(stonePainter, size = RESOURCE_ICON_SIZE),
            ),
            fallback = nil(),
        )
    }

    CircleLayer(
        id = CLUSTER_LAYER_ID,
        source = source,
        maxZoom = DECLUSTER_ZOOM.toFloat(),
        filter = feature[PROPERTY_IS_CLUSTER].asBoolean(const(false)) eq const(true),
        color = const(Color(0xFF2E7D32)),
        opacity = const(0.85f),
        radius = step(
            input = feature[PROPERTY_CLUSTER_POINT_COUNT].asNumber(const(0f)),
            fallback = const(16.dp),
            20 to const(20.dp),
            50 to const(24.dp),
        ),
    )

    SymbolLayer(
        id = CLUSTER_COUNT_LAYER_ID,
        source = source,
        maxZoom = DECLUSTER_ZOOM.toFloat(),
        filter = feature[PROPERTY_IS_CLUSTER].asBoolean(const(false)) eq const(true),
        textField = format(
            span(
                feature[PROPERTY_CLUSTER_POINT_COUNT_ABBREVIATED].asString(const("")),
            ),
        ),
        textColor = const(Color.White),
        textSize = const(12.sp),
    )

    CircleLayer(
        id = OBJECT_LAYER_ID,
        source = source,
        minZoom = DECLUSTER_ZOOM.toFloat(),
        filter = feature[PROPERTY_OBJECT_KIND].asString(const("")) eq const(MapObjectKind.PointOfInterest.idPrefix),
        color = const(Color(0xFF3949AB)),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
        radius = const(8.dp),
        onClick = { features ->
            resolveObjectId(features)?.let { objectId ->
                onObjectTapped(objectId)
                ClickResult.Consume
            } ?: ClickResult.Pass
        },
    )

    SymbolLayer(
        id = RESOURCE_SPAWN_LAYER_ID,
        source = source,
        minZoom = DECLUSTER_ZOOM.toFloat(),
        filter = feature[PROPERTY_OBJECT_KIND].asString(const("")) eq const(MapObjectKind.ResourceSpawn.idPrefix),
        iconImage = resourceSpawnIcon,
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        onClick = { features ->
            resolveObjectId(features)?.let { objectId ->
                onObjectTapped(objectId)
                ClickResult.Consume
            } ?: ClickResult.Pass
        },
    )
}

internal fun List<MapObjectUiModel>.toFeatureCollection(): FeatureCollection<Point, JsonObject> =
    FeatureCollection(
        features = map { objectModel ->
            Feature(
                geometry = Point(
                    longitude = objectModel.position.longitude,
                    latitude = objectModel.position.latitude,
                ),
                properties = objectModel.toFeatureProperties(),
                id = JsonPrimitive(objectModel.id),
            )
        },
    )

internal fun List<MapObjectUiModel>.toGeoJsonDataOrNull(): GeoJsonData.Features? =
    takeIf { it.isNotEmpty() }
        ?.toFeatureCollection()
        ?.let(GeoJsonData::Features)

internal fun MapObjectUiModel.toFeatureProperties(): JsonObject = buildJsonObject {
    put(PROPERTY_OBJECT_ID, id)
    put(PROPERTY_OBJECT_KIND, kind.idPrefix)
    put(PROPERTY_OBJECT_TITLE, title)
    description?.let { put(PROPERTY_OBJECT_DESCRIPTION, it) }
    put(PROPERTY_OBJECT_RADIUS_METERS, radiusMeters)
    put(PROPERTY_OBJECT_IS_DISCOVERED, isDiscovered)
    resourceType?.let { put(PROPERTY_OBJECT_RESOURCE_TYPE_ID, it.typeId) }
}

internal fun resolveObjectId(features: List<Feature<*, JsonObject?>>): String? =
    features.firstNotNullOfOrNull { feature ->
        feature.properties
            ?.get(PROPERTY_OBJECT_ID)
            ?.jsonPrimitive
            ?.contentOrNull
    }

internal const val GAME_ENTITIES_SOURCE_ID = "game-entities-source"
internal const val CLUSTER_LAYER_ID = "game-entities-cluster-layer"
internal const val CLUSTER_COUNT_LAYER_ID = "game-entities-cluster-count-layer"
internal const val OBJECT_LAYER_ID = "game-entities-object-layer"
internal const val RESOURCE_SPAWN_LAYER_ID = "resource-spawn-layer"

internal const val PROPERTY_OBJECT_ID = "object_id"
internal const val PROPERTY_OBJECT_KIND = "kind"
internal const val PROPERTY_OBJECT_TITLE = "title"
internal const val PROPERTY_OBJECT_DESCRIPTION = "description"
internal const val PROPERTY_OBJECT_RADIUS_METERS = "radius_meters"
internal const val PROPERTY_OBJECT_IS_DISCOVERED = "is_discovered"
internal const val PROPERTY_OBJECT_RESOURCE_TYPE_ID = "resource_type_id"
internal const val PROPERTY_IS_CLUSTER = "cluster"
internal const val PROPERTY_CLUSTER_POINT_COUNT = "point_count"
internal const val PROPERTY_CLUSTER_POINT_COUNT_ABBREVIATED = "point_count_abbreviated"

internal const val DECLUSTER_ZOOM = 12
internal const val CLUSTER_RADIUS = 60
private val RESOURCE_ICON_SIZE = DpSize(32.dp, 32.dp)

@Composable
private fun rememberResourceTypePainter(resourceType: ResourceType): Painter {
    val context = LocalContext.current
    val drawableId = remember(context.packageName, resourceType) {
        context.resolveDrawableId(resourceType.drawableName)
    }

    require(drawableId != 0) {
        "Missing drawable resource for ${resourceType.typeId}."
    }

    return painterResource(id = drawableId)
}

private fun Context.resolveDrawableId(drawableName: String): Int =
    resources.getIdentifier(drawableName, "drawable", packageName)
