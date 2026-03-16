package com.github.arhor.journey.feature.map

import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileRange
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test

class FogOfWarCalculatorTest {

    @Test
    fun `calculateUnexploredFogRanges should merge vertical runs when adjacent rows share the same unexplored span`() {
        // Given
        val visibleRange = ExplorationTileRange(
            zoom = 16,
            minX = 0,
            maxX = 2,
            minY = 0,
            maxY = 2,
        )
        val exploredTiles = setOf(
            ExplorationTile(zoom = 16, x = 1, y = 2),
        )

        // When
        val actual = calculateUnexploredFogRanges(
            visibleRange = visibleRange,
            exploredTiles = exploredTiles,
        )

        // Then
        actual shouldContainExactly listOf(
            ExplorationTileRange(zoom = 16, minX = 0, maxX = 2, minY = 0, maxY = 1),
            ExplorationTileRange(zoom = 16, minX = 0, maxX = 0, minY = 2, maxY = 2),
            ExplorationTileRange(zoom = 16, minX = 2, maxX = 2, minY = 2, maxY = 2),
        )
    }

    @Test
    fun `calculateUnexploredFogRanges should return empty when every visible tile is already explored`() {
        // Given
        val visibleRange = ExplorationTileRange(
            zoom = 16,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val exploredTiles = visibleRange.asSequence().toSet()

        // When
        val actual = calculateUnexploredFogRanges(
            visibleRange = visibleRange,
            exploredTiles = exploredTiles,
        )

        // Then
        actual.isEmpty() shouldBe true
    }
}
