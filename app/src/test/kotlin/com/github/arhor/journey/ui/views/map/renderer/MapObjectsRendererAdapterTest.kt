package com.github.arhor.journey.ui.views.map.renderer

import com.github.arhor.journey.domain.model.LatLng
import com.github.arhor.journey.ui.views.map.model.MapObjectUiModel
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point

class MapObjectsRendererAdapterTest {

    @Test
    fun `toGeoJsonDataOrNull should return null when objects are empty`() {
        // Given
        val objects = emptyList<MapObjectUiModel>()

        // When
        val geoJsonData = objects.toGeoJsonDataOrNull()

        // Then
        geoJsonData.shouldBeNull()
    }

    @Test
    fun `toFeatureCollection should map object metadata and id into feature properties when objects are provided`() {
        // Given
        val objects = listOf(
            MapObjectUiModel(
                id = "poi-1",
                title = "Town Square",
                description = "Main plaza",
                position = LatLng(latitude = 48.8584, longitude = 2.2945),
                radiusMeters = 120,
                isDiscovered = true,
            ),
        )

        // When
        val featureCollection = objects.toFeatureCollection()

        // Then
        featureCollection shouldHaveSize 1
        val feature = featureCollection.first()
        feature.id shouldBe JsonPrimitive("poi-1")
        feature.geometry shouldBe Point(longitude = 2.2945, latitude = 48.8584)
        feature.properties[PROPERTY_OBJECT_ID] shouldBe JsonPrimitive("poi-1")
        feature.properties[PROPERTY_OBJECT_TITLE] shouldBe JsonPrimitive("Town Square")
        feature.properties[PROPERTY_OBJECT_DESCRIPTION] shouldBe JsonPrimitive("Main plaza")
        feature.properties[PROPERTY_OBJECT_RADIUS_METERS] shouldBe JsonPrimitive(120)
        feature.properties[PROPERTY_OBJECT_IS_DISCOVERED] shouldBe JsonPrimitive(true)
    }

    @Test
    fun `toGeoJsonDataOrNull should wrap feature collection when objects are provided`() {
        // Given
        val objects = listOf(
            MapObjectUiModel(
                id = "poi-1",
                title = "Town Square",
                description = "Main plaza",
                position = LatLng(latitude = 48.8584, longitude = 2.2945),
                radiusMeters = 120,
                isDiscovered = true,
            ),
        )

        // When
        val geoJsonData = objects.toGeoJsonDataOrNull()

        // Then
        geoJsonData shouldBe GeoJsonData.Features(objects.toFeatureCollection())
    }

    @Test
    fun `resolveObjectId should return first object id when tapped features include game entity metadata`() {
        // Given
        val features = listOf(
            Feature(
                geometry = Point(longitude = 0.0, latitude = 0.0),
                properties = MapObjectUiModel(
                    id = "poi-42",
                    title = "Ancient Oak",
                    description = null,
                    position = LatLng(latitude = 0.0, longitude = 0.0),
                    radiusMeters = 50,
                    isDiscovered = false,
                ).toFeatureProperties(),
                id = JsonPrimitive("poi-42"),
            ),
        )

        // When
        val objectId = resolveObjectId(features)

        // Then
        objectId shouldBe "poi-42"
    }

    @Test
    fun `resolveObjectId should return null when tapped features do not include object id property`() {
        // Given
        val features = listOf(
            Feature(
                geometry = Point(longitude = 1.0, latitude = 1.0),
                properties = kotlinx.serialization.json.buildJsonObject {
                    put("name", JsonPrimitive("cluster"))
                },
                id = JsonPrimitive("cluster-1"),
            ),
        )

        // When
        val objectId = resolveObjectId(features)

        // Then
        objectId shouldBe null
    }
}
