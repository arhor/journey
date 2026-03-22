package com.github.arhor.journey.feature.map

import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds
import io.kotest.matchers.shouldBe
import org.junit.Test

class FogOfWarBufferingStrategyTest {

    @Test
    fun `buffer region should keep trigger bounds nested inside buffered bounds`() {
        // Given
        val visibleTileRange = ExplorationTileRange(
            zoom = 17,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )

        // When
        val actual = createFogBufferRegion(visibleTileRange)

        // Then
        actual.bufferedTileRange.contains(actual.triggerTileRange) shouldBe true
        actual.bufferedBounds.strictlyContains(actual.triggerBounds) shouldBe true
    }

    @Test
    fun `buffer region should not request recompute while viewport remains fully inside trigger bounds`() {
        // Given
        val visibleBounds = GeoBounds(
            south = 50.0,
            west = 30.0,
            north = 50.01,
            east = 30.01,
        )
        val bufferRegion = createFogBufferRegion(
            visibleBounds = visibleBounds,
            canonicalZoom = 17,
        )

        // When
        val actual = bufferRegion.shouldRecompute(visibleBounds)

        // Then
        actual shouldBe false
    }

    @Test
    fun `buffer region should request recompute when viewport touches trigger bounds`() {
        // Given
        val triggerBounds = GeoBounds(
            south = 50.0,
            west = 30.0,
            north = 50.1,
            east = 30.1,
        )
        val bufferRegion = FogBufferRegion(
            triggerBounds = triggerBounds,
            bufferedBounds = GeoBounds(
                south = 49.9,
                west = 29.9,
                north = 50.2,
                east = 30.2,
            ),
            triggerTileRange = ExplorationTileRange(
                zoom = 17,
                minX = 10,
                maxX = 12,
                minY = 20,
                maxY = 22,
            ),
            bufferedTileRange = ExplorationTileRange(
                zoom = 17,
                minX = 9,
                maxX = 13,
                minY = 19,
                maxY = 23,
            ),
        )
        val touchingViewport = GeoBounds(
            south = 50.02,
            west = 30.02,
            north = 50.08,
            east = triggerBounds.east,
        )

        // When
        val actual = bufferRegion.shouldRecompute(touchingViewport)

        // Then
        actual shouldBe true
    }
}
