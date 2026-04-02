package com.github.arhor.journey.feature.map.fow.ui

import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.feature.map.fow.FowRenderDataFactory
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderState
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test
import org.maplibre.compose.sources.GeoJsonData

class FogOfWarMapOverlayTest {

    @Test
    fun `fogOfWarLayerSpecs should return hidden explored layer first then handoff and active fog layers`() {
        // Given
        val renderDataFactory = FowRenderDataFactory()
        val hiddenExploredRenderData = renderDataFactory.createFullRange(
            ExplorationTileRange(
                zoom = 17,
                minX = 6,
                maxX = 7,
                minY = 16,
                maxY = 17,
            ),
        )
        val handoffRenderData = renderDataFactory.createFullRange(
            ExplorationTileRange(
                zoom = 17,
                minX = 8,
                maxX = 9,
                minY = 18,
                maxY = 19,
            ),
        )
        val activeRenderData = renderDataFactory.createFullRange(
            ExplorationTileRange(
                zoom = 17,
                minX = 10,
                maxX = 11,
                minY = 20,
                maxY = 21,
            ),
        )
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
    fun `fogOfWarLayerSpecs should keep stable layer order when handoff is empty`() {
        // Given
        val renderDataFactory = FowRenderDataFactory()
        val activeRenderData = renderDataFactory.createFullRange(
            ExplorationTileRange(
                zoom = 17,
                minX = 10,
                maxX = 11,
                minY = 20,
                maxY = 21,
            ),
        )
        val state = FogOfWarRenderState(
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
        actual[0].renderData shouldBe null
        actual[1].renderData shouldBe null
        actual.map { it.isVisible } shouldContainExactly listOf(
            false,
            false,
            true,
        )
    }

    @Test
    fun `empty fog fallback should use raw json feature collection`() {
        EMPTY_FOG_GEO_JSON_DATA.shouldBeInstanceOf<GeoJsonData.JsonString>()
            .json shouldBe """{"type":"FeatureCollection","features":[]}"""
    }
}
