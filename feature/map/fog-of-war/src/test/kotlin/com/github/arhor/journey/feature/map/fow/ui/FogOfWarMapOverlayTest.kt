package com.github.arhor.journey.feature.map.fow.ui

import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.feature.map.fow.FowRenderDataFactory
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderState
import com.github.arhor.journey.feature.map.fow.model.FogOfWarUiState
import com.github.arhor.journey.feature.map.fow.model.renderState
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.shouldBe
import org.maplibre.compose.sources.GeoJsonData
import org.junit.Test

class FogOfWarMapOverlayTest {

    @Test
    fun `fogOfWarLayerSpecs should return handoff layer first and active layer second`() {
        // Given
        val renderDataFactory = FowRenderDataFactory()
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
            handoffRenderData = handoffRenderData,
            activeRenderData = activeRenderData,
        )

        // When
        val actual = state.fogOfWarLayerSpecs()

        // Then
        actual.map { it.sourceId to it.layerId } shouldContainExactly listOf(
            HANDOFF_FOG_OF_WAR_SOURCE_ID to HANDOFF_FOG_OF_WAR_LAYER_ID,
            ACTIVE_FOG_OF_WAR_SOURCE_ID to ACTIVE_FOG_OF_WAR_LAYER_ID,
        )
        actual.map { it.renderData } shouldContainExactly listOf(
            handoffRenderData,
            activeRenderData,
        )
        actual.map { it.isVisible } shouldContainExactly listOf(
            true,
            true,
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
            HANDOFF_FOG_OF_WAR_SOURCE_ID to HANDOFF_FOG_OF_WAR_LAYER_ID,
            ACTIVE_FOG_OF_WAR_SOURCE_ID to ACTIVE_FOG_OF_WAR_LAYER_ID,
        )
        actual.first().renderData shouldBe null
        actual.map { it.isVisible } shouldContainExactly listOf(
            false,
            true,
        )
    }

    @Test
    fun `empty fog fallback should use raw json feature collection`() {
        EMPTY_FOG_GEO_JSON_DATA.shouldBeInstanceOf<GeoJsonData.JsonString>()
            .json shouldBe """{"type":"FeatureCollection","features":[]}"""
    }

    @Test
    fun `renderState should keep the same render payload when only diagnostics fields differ`() {
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
        val baseline = FogOfWarUiState(
            activeRenderData = activeRenderData,
            visibleTileCount = 100,
            isRecomputing = false,
        )
        val updatedDiagnostics = baseline.copy(
            visibleTileCount = 101,
            isRecomputing = true,
        )

        // When
        val baselineRenderState = baseline.renderState
        val updatedRenderState = updatedDiagnostics.renderState

        // Then
        baselineRenderState shouldBe updatedRenderState
        baseline shouldNotBe updatedDiagnostics
    }
}
