package com.github.arhor.journey.feature.map.viewinterop

import com.github.arhor.journey.feature.map.model.BreachMarkerState
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapObjectKind
import com.github.arhor.journey.feature.map.model.MapObjectUiModel
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.maplibre.geojson.Point

class MapObjectLayerControllerTest {

    private val subject = MapObjectLayerController()

    @Test
    fun `toFeatureCollection should sort map objects by id and keep object ids in feature properties`() {
        // Given
        val objects = listOf(
            mapObject(
                id = "breach-node:v1:h3r9:cell-b",
                latitude = 50.46,
                longitude = 30.53,
                markerState = BreachMarkerState.CONTROLLED,
            ),
            mapObject(
                id = "breach-node:v1:h3r9:cell-a",
                latitude = 50.45,
                longitude = 30.52,
                markerState = BreachMarkerState.UPLOAD_READY,
            ),
        )

        // When
        val actual = subject.toFeatureCollection(objects)

        // Then
        actual.features()?.map { it.getStringProperty(MAP_OBJECT_ID_PROPERTY) } shouldContainExactly listOf(
            "breach-node:v1:h3r9:cell-a",
            "breach-node:v1:h3r9:cell-b",
        )
        actual.features()?.map { it.getStringProperty("markerState") } shouldContainExactly listOf(
            BreachMarkerState.UPLOAD_READY.name,
            BreachMarkerState.CONTROLLED.name,
        )
        val firstPoint = actual.features()?.first()?.geometry() as Point
        firstPoint.latitude() shouldBe 50.45
        firstPoint.longitude() shouldBe 30.52
    }

    private fun mapObject(
        id: String,
        latitude: Double,
        longitude: Double,
        markerState: BreachMarkerState,
    ): MapObjectUiModel =
        MapObjectUiModel(
            id = id,
            kind = MapObjectKind.BreachNode,
            title = "District",
            description = "Recovered node",
            position = LatLng(
                latitude = latitude,
                longitude = longitude,
            ),
            radiusMeters = 35,
            isDiscovered = true,
            markerState = markerState,
        )
}
