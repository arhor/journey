package com.github.arhor.journey.feature.map.renderer

import com.github.arhor.journey.domain.model.ExplorationTileRange
import io.kotest.matchers.shouldBe
import org.junit.Test

class TilesGridRendererAdapterTest {

    @Test
    fun `toTileGridFeatureCollection should emit one polygon per visible tile`() {
        // Given
        val tileRange = ExplorationTileRange(
            zoom = 16,
            minX = 34567,
            maxX = 34568,
            minY = 22345,
            maxY = 22346,
        )

        // When
        val actual = tileRange.toTileGridFeatureCollection()

        // Then
        actual.features.size shouldBe 4
    }

    @Test
    fun `toTileGridFeatureCollection should emit closed tile polygons`() {
        // Given
        val tileRange = ExplorationTileRange(
            zoom = 16,
            minX = 34567,
            maxX = 34567,
            minY = 22345,
            maxY = 22345,
        )

        // When
        val actual = tileRange.toTileGridFeatureCollection()

        // Then
        val coordinates = actual.features.single().geometry.coordinates.single()
        coordinates.first() shouldBe coordinates.last()
        coordinates.size shouldBe 5
    }
}
