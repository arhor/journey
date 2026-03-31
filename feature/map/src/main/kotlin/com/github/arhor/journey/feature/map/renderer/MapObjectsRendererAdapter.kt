package com.github.arhor.journey.feature.map.renderer

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
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

    val scrapBitmap = rememberResourceTypeImageBitmap(ResourceType.SCRAP)
    val componentsBitmap = rememberResourceTypeImageBitmap(ResourceType.COMPONENTS)
    val fuelBitmap = rememberResourceTypeImageBitmap(ResourceType.FUEL)
    val unknownResourceBitmap = rememberDrawableImageBitmap(RESOURCE_UNKNOWN_DRAWABLE_NAME)
    val density = LocalDensity.current
    val resourceIconScale = remember(
        density.density,
        scrapBitmap.width,
        scrapBitmap.height,
        componentsBitmap.width,
        componentsBitmap.height,
        fuelBitmap.width,
        fuelBitmap.height,
        unknownResourceBitmap.width,
        unknownResourceBitmap.height,
    ) {
        with(density) {
            RESOURCE_ICON_SIZE.toPx() / maxOf(
                scrapBitmap.width,
                scrapBitmap.height,
                componentsBitmap.width,
                componentsBitmap.height,
                fuelBitmap.width,
                fuelBitmap.height,
                unknownResourceBitmap.width,
                unknownResourceBitmap.height,
            ).toFloat()
        }
    }
    val resourceSpawnIcon = remember(scrapBitmap, componentsBitmap, fuelBitmap, unknownResourceBitmap) {
        switch(
            input = feature[PROPERTY_OBJECT_RESOURCE_ICON_KEY].asString(const("")),
            case(
                label = ResourceType.SCRAP.typeId,
                output = image(scrapBitmap),
            ),
            case(
                label = ResourceType.COMPONENTS.typeId,
                output = image(componentsBitmap),
            ),
            case(
                label = ResourceType.FUEL.typeId,
                output = image(fuelBitmap),
            ),
            case(
                label = RESOURCE_UNKNOWN_DRAWABLE_NAME,
                output = image(unknownResourceBitmap),
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
        iconSize = const(resourceIconScale),
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
    put(PROPERTY_OBJECT_IS_HIDDEN_BY_FOG, isHiddenByFog)
    resourceType?.let { put(PROPERTY_OBJECT_RESOURCE_TYPE_ID, it.typeId) }
    resourceIconKey()?.let { put(PROPERTY_OBJECT_RESOURCE_ICON_KEY, it) }
}

internal fun MapObjectUiModel.resourceIconKey(): String? = when {
    kind != MapObjectKind.ResourceSpawn -> null
    isHiddenByFog -> RESOURCE_UNKNOWN_DRAWABLE_NAME
    else -> resourceType?.typeId
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
internal const val PROPERTY_OBJECT_IS_HIDDEN_BY_FOG = "is_hidden_by_fog"
internal const val PROPERTY_OBJECT_RESOURCE_TYPE_ID = "resource_type_id"
internal const val PROPERTY_OBJECT_RESOURCE_ICON_KEY = "resource_icon_key"
internal const val PROPERTY_IS_CLUSTER = "cluster"
internal const val PROPERTY_CLUSTER_POINT_COUNT = "point_count"
internal const val PROPERTY_CLUSTER_POINT_COUNT_ABBREVIATED = "point_count_abbreviated"

internal const val DECLUSTER_ZOOM = 12
internal const val CLUSTER_RADIUS = 60
private const val RESOURCE_UNKNOWN_DRAWABLE_NAME = "resource_unknown"
private val RESOURCE_ICON_SIZE = 20.dp

@Composable
private fun rememberResourceTypeImageBitmap(resourceType: ResourceType): ImageBitmap {
    return rememberDrawableImageBitmap(resourceType.drawableName)
}

@Composable
private fun rememberDrawableImageBitmap(drawableName: String): ImageBitmap {
    val context = LocalContext.current
    val drawableId = remember(context.packageName, drawableName) {
        context.resolveDrawableId(drawableName)
    }

    require(drawableId != 0) {
        "Missing drawable resource for $drawableName."
    }

    return ImageBitmap.imageResource(id = drawableId)
}

private fun Context.resolveDrawableId(drawableName: String): Int =
    resources.getIdentifier(drawableName, "drawable", packageName)
