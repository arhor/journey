package com.github.arhor.journey.feature.map.renderer

import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapObjectKind
import com.github.arhor.journey.feature.map.model.MapObjectUiModel
import com.github.arhor.journey.feature.map.model.WatchtowerMarkerState
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point

class MapObjectsRendererAdapterTest {

    @Test
    fun `toGeoJsonDataOrNull should return null when objects list is empty`() {
        // Given
        val objects = emptyList<MapObjectUiModel>()

        // When
        val actual = objects.toGeoJsonDataOrNull()

        // Then
        actual.shouldBeNull()
    }

    @Test
    fun `toFeatureCollection should map objects into features when objects list has elements`() {
        // Given
        val objectUiModel = mapObject(
            id = "poi-1",
            title = "Old Tower",
            description = "Historic tower",
            latitude = 49.84,
            longitude = 24.03,
            radiusMeters = 120,
            isDiscovered = true,
            kind = MapObjectKind.PointOfInterest,
        )

        // When
        val actual = listOf(objectUiModel).toFeatureCollection()

        // Then
        actual.features.size shouldBe 1

        val feature = actual.features.single()
        feature.geometry shouldBe Point(longitude = 24.03, latitude = 49.84)
        feature.id shouldBe JsonPrimitive("poi-1")
        feature.properties.get(PROPERTY_OBJECT_KIND)?.jsonPrimitive?.contentOrNull shouldBe "poi"
        feature.properties.get(PROPERTY_OBJECT_TITLE)?.jsonPrimitive?.contentOrNull shouldBe "Old Tower"
        feature.properties.get(PROPERTY_OBJECT_DESCRIPTION)?.jsonPrimitive?.contentOrNull shouldBe "Historic tower"
        feature.properties.get(PROPERTY_OBJECT_RADIUS_METERS)?.jsonPrimitive?.contentOrNull shouldBe "120"
        feature.properties.get(PROPERTY_OBJECT_IS_DISCOVERED)?.jsonPrimitive?.contentOrNull shouldBe "true"
        feature.properties.get(PROPERTY_OBJECT_IS_HIDDEN_BY_FOG)?.jsonPrimitive?.contentOrNull shouldBe "false"
    }

    @Test
    fun `toFeatureProperties should omit description when object description is null`() {
        // Given
        val objectUiModel = mapObject(
            id = "poi-2",
            title = "Bridge",
            description = null,
            latitude = 50.45,
            longitude = 30.52,
            radiusMeters = 80,
            isDiscovered = false,
            kind = MapObjectKind.PointOfInterest,
        )

        // When
        val actual = objectUiModel.toFeatureProperties()

        // Then
        actual[PROPERTY_OBJECT_ID]?.jsonPrimitive?.contentOrNull shouldBe "poi-2"
        actual[PROPERTY_OBJECT_KIND]?.jsonPrimitive?.contentOrNull shouldBe "poi"
        actual[PROPERTY_OBJECT_TITLE]?.jsonPrimitive?.contentOrNull shouldBe "Bridge"
        actual.containsKey(PROPERTY_OBJECT_DESCRIPTION) shouldBe false
        actual[PROPERTY_OBJECT_RADIUS_METERS]?.jsonPrimitive?.contentOrNull shouldBe "80"
        actual[PROPERTY_OBJECT_IS_DISCOVERED]?.jsonPrimitive?.contentOrNull shouldBe "false"
        actual[PROPERTY_OBJECT_IS_HIDDEN_BY_FOG]?.jsonPrimitive?.contentOrNull shouldBe "false"
    }

    @Test
    fun `toFeatureProperties should include resource type when object is a resource spawn`() {
        // Given
        val objectUiModel = mapObject(
            id = "spawn-1",
            title = "Scrap",
            description = null,
            latitude = 50.45,
            longitude = 30.52,
            radiusMeters = 25,
            isDiscovered = false,
            kind = MapObjectKind.ResourceSpawn,
            isHiddenByFog = true,
            resourceType = ResourceType.SCRAP,
        )

        // When
        val actual = objectUiModel.toFeatureProperties()

        // Then
        actual[PROPERTY_OBJECT_RESOURCE_TYPE_ID]?.jsonPrimitive?.contentOrNull shouldBe "scrap"
        actual[PROPERTY_OBJECT_IS_HIDDEN_BY_FOG]?.jsonPrimitive?.contentOrNull shouldBe "true"
    }

    @Test
    fun `toFeatureProperties should include watchtower marker metadata when object is a watchtower`() {
        // Given
        val objectUiModel = mapObject(
            id = "watchtower-1",
            title = "Old Town Spire",
            description = "Dormant",
            latitude = 51.1093,
            longitude = 17.0326,
            radiusMeters = 150,
            isDiscovered = true,
            kind = MapObjectKind.Watchtower,
            watchtowerMarkerState = WatchtowerMarkerState.CLAIMABLE,
            watchtowerLevel = 1,
        )

        // When
        val actual = objectUiModel.toFeatureProperties()

        // Then
        actual[PROPERTY_OBJECT_WATCHTOWER_STATE]?.jsonPrimitive?.contentOrNull shouldBe
            WatchtowerMarkerState.CLAIMABLE.name
        actual[PROPERTY_OBJECT_WATCHTOWER_LEVEL]?.jsonPrimitive?.contentOrNull shouldBe "1"
    }

    @Test
    fun `resolveObjectId should return first matching object id when features contain object ids`() {
        // Given
        val features = listOf(
            featureWithObjectId(objectId = null),
            featureWithObjectId(objectId = "poi-3"),
            featureWithObjectId(objectId = "poi-4"),
        )

        // When
        val actual = resolveObjectId(features)

        // Then
        actual shouldBe "poi-3"
    }

    @Test
    fun `resolveObjectId should return null when no features contain object id property`() {
        // Given
        val features = listOf(
            featureWithObjectId(objectId = null),
            featureWithObjectId(objectId = null),
        )

        // When
        val actual = resolveObjectId(features)

        // Then
        actual.shouldBeNull()
    }

    @Test
    fun `toGeoJsonDataOrNull should return features data when objects list is not empty`() {
        // Given
        val objects = listOf(
            mapObject(
                id = "poi-5",
                title = "Library",
                description = "City library",
                latitude = 48.2,
                longitude = 16.37,
                radiusMeters = 90,
                isDiscovered = true,
                kind = MapObjectKind.PointOfInterest,
            ),
        )

        // When
        val actual = objects.toGeoJsonDataOrNull()

        // Then
        actual shouldNotBe null
    }

    private fun mapObject(
        id: String,
        title: String,
        description: String?,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        isDiscovered: Boolean,
        kind: MapObjectKind,
        isHiddenByFog: Boolean = false,
        resourceType: ResourceType? = null,
        watchtowerMarkerState: WatchtowerMarkerState? = null,
        watchtowerLevel: Int? = null,
    ): MapObjectUiModel = MapObjectUiModel(
        id = id,
        kind = kind,
        title = title,
        description = description,
        position = LatLng(latitude = latitude, longitude = longitude),
        radiusMeters = radiusMeters,
        isDiscovered = isDiscovered,
        isHiddenByFog = isHiddenByFog,
        resourceType = resourceType,
        watchtowerMarkerState = watchtowerMarkerState,
        watchtowerLevel = watchtowerLevel,
    )

    private fun featureWithObjectId(objectId: String?): Feature<Point, kotlinx.serialization.json.JsonObject?> = Feature(
        geometry = Point(longitude = 24.0, latitude = 49.0),
        properties = buildJsonObject {
            if (objectId != null) {
                put(PROPERTY_OBJECT_ID, objectId)
            }
        },
        id = objectId?.let(::JsonPrimitive),
    )
}
