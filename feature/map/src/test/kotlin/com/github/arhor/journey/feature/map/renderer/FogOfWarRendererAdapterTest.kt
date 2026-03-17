package com.github.arhor.journey.feature.map.renderer

import com.github.arhor.journey.domain.model.ExplorationTileRange
import io.kotest.matchers.shouldBe
import org.junit.Test

class FogOfWarRendererAdapterTest {

    @Test
    fun `toFeatureCollection should assign empty properties when fog ranges are converted to features`() {
        // Given
        val fogRanges = listOf(
            ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34568,
                minY = 22345,
                maxY = 22346,
            ),
        )

        // When
        val actual = fogRanges.toFeatureCollection()

        // Then
        actual.features.single().properties?.isEmpty() shouldBe true
    }

    @Test
    fun `toFeatureCollection should emit rounded merged polygons for connected fog tiles`() {
        // Given
        val fogRanges = listOf(
            ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34568,
                minY = 22345,
                maxY = 22345,
            ),
            ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34567,
                minY = 22346,
                maxY = 22346,
            ),
        )

        // When
        val actual = fogRanges.toFeatureCollection()

        // Then
        (actual.features.single().geometry.coordinates.single().size > 5) shouldBe true
    }
}
