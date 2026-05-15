package com.github.arhor.journey.feature.map.viewinterop

import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderData
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderState
import com.github.arhor.journey.feature.map.fow.model.ACTIVE_FOG_OF_WAR_LAYER_ID
import com.github.arhor.journey.feature.map.fow.model.ACTIVE_FOG_OF_WAR_OPACITY
import com.github.arhor.journey.feature.map.fow.model.ACTIVE_FOG_OF_WAR_SOURCE_ID
import com.github.arhor.journey.feature.map.fow.model.HANDOFF_FOG_OF_WAR_LAYER_ID
import com.github.arhor.journey.feature.map.fow.model.HANDOFF_FOG_OF_WAR_SOURCE_ID
import com.github.arhor.journey.feature.map.fow.model.HIDDEN_EXPLORED_LAYER_ID
import com.github.arhor.journey.feature.map.fow.model.HIDDEN_EXPLORED_OPACITY
import com.github.arhor.journey.feature.map.fow.model.HIDDEN_EXPLORED_SOURCE_ID
import com.github.arhor.journey.feature.map.fow.model.fogOfWarLayerSpecs
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapObjectKind
import com.github.arhor.journey.feature.map.model.MapObjectUiModel
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point

class NativeFogOfWarLayerControllerTest {

    @Test
    fun `updateMapLayerControllers should forward fog state to native fog controller`() {
        // Given
        val fogLayerController = mockk<NativeFogOfWarLayerController>(relaxed = true)
        val objectLayerController = mockk<MapObjectLayerController>(relaxed = true)
        val fogOfWar = FogOfWarRenderState(
            activeRenderData = renderData("active"),
        )
        val visibleObjects = listOf(
            MapObjectUiModel(
                id = "breach-node:v1:h3r9:cell-1",
                kind = MapObjectKind.BreachNode,
                title = "District 9",
                description = null,
                position = LatLng(latitude = 50.45, longitude = 30.52),
                radiusMeters = 35,
                isDiscovered = true,
            ),
        )

        every { fogLayerController.update(fogOfWar) } returns Unit
        every { objectLayerController.update(visibleObjects) } returns Unit

        // When
        updateMapLayerControllers(
            fogLayerController = fogLayerController,
            fogOfWar = fogOfWar,
            objectLayerController = objectLayerController,
            visibleObjects = visibleObjects,
        )

        // Then
        verify(exactly = 1) { fogLayerController.update(fogOfWar) }
        verify(exactly = 1) { objectLayerController.update(visibleObjects) }
    }

    @Test
    fun `nativeFogOfWarLayerSpecs should return hidden explored layer first then handoff and active fog layers`() {
        // Given
        val hiddenExploredRenderData = renderData("hidden")
        val handoffRenderData = renderData("handoff")
        val activeRenderData = renderData("active")
        val state = FogOfWarRenderState(
            hiddenExploredRenderData = hiddenExploredRenderData,
            handoffRenderData = handoffRenderData,
            activeRenderData = activeRenderData,
        )

        // When
        val actual = state.fogOfWarLayerSpecs()

        // Then
        actual.map { it.sourceId to it.layerId } shouldContainExactly listOf(
            HIDDEN_EXPLORED_SOURCE_ID to HIDDEN_EXPLORED_LAYER_ID,
            HANDOFF_FOG_OF_WAR_SOURCE_ID to HANDOFF_FOG_OF_WAR_LAYER_ID,
            ACTIVE_FOG_OF_WAR_SOURCE_ID to ACTIVE_FOG_OF_WAR_LAYER_ID,
        )
        actual.map { it.renderData } shouldContainExactly listOf(
            hiddenExploredRenderData,
            handoffRenderData,
            activeRenderData,
        )
        actual.map { it.isVisible } shouldContainExactly listOf(
            true,
            true,
            true,
        )
        actual.map { it.opacity } shouldContainExactly listOf(
            HIDDEN_EXPLORED_OPACITY,
            ACTIVE_FOG_OF_WAR_OPACITY,
            ACTIVE_FOG_OF_WAR_OPACITY,
        )
    }

    @Test
    fun `nativeFogOfWarLayerSpecs should hide layers with null render data`() {
        // Given
        val activeRenderData = renderData("active")
        val state = FogOfWarRenderState(
            activeRenderData = activeRenderData,
        )

        // When
        val actual = state.fogOfWarLayerSpecs()

        // Then
        actual.map { it.isVisible } shouldContainExactly listOf(
            false,
            false,
            true,
        )
        actual.map { it.renderData } shouldContainExactly listOf(
            null,
            null,
            activeRenderData,
        )
    }

    @Test
    fun `toNativeFogGeoJson should use empty feature collection for null render data`() {
        // When
        val actual = null.toNativeFogGeoJson()

        // Then
        actual shouldBe EMPTY_NATIVE_FOG_GEO_JSON
    }

    @Test
    fun `toNativeFogGeoJson should convert render data features to json`() {
        // Given
        val renderData = renderData("active")

        // When
        val actual = renderData.toNativeFogGeoJson()

        // Then
        actual shouldBe """
            |{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[30.0,10.0]},"properties":{"layer":"active"}}]}
        """.trimMargin()
    }

    private fun renderData(layer: String): FogOfWarRenderData =
        FogOfWarRenderData(
            featureCollection = FeatureCollection(
                listOf(
                    Feature(
                        geometry = Point(longitude = 30.0, latitude = 10.0),
                        properties = buildJsonObject {
                            put("layer", layer)
                        },
                    ),
                ),
            ),
        )
}
