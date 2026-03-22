package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileRange
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test

class FogOfWarCalculatorTest {

    @Test
    fun `calculateUnexploredFogRanges should extend fog one tile past the left viewport edge when the border remains unexplored`() {
        // Given
        val visibleRange = ExplorationTileRange(
            zoom = 16,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val fogTileRange = visibleRange.expandedBy(tilePadding = 1)
        val exploredTiles = fogTileRange.asSequence()
            .filter { tile -> tile.x >= 11 }
            .toSet()

        // When
        val actual = calculateUnexploredFogRanges(
            tileRange = fogTileRange,
            exploredTiles = exploredTiles,
        )

        // Then
        actual shouldContainExactly listOf(
            ExplorationTileRange(
                zoom = 16,
                minX = 9,
                maxX = 10,
                minY = 19,
                maxY = 22,
            ),
        )
    }

    @Test
    fun `calculateUnexploredFogRanges should extend fog past a viewport corner when the corner remains unexplored`() {
        // Given
        val visibleRange = ExplorationTileRange(
            zoom = 16,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val fogTileRange = visibleRange.expandedBy(tilePadding = 1)
        val exploredTiles = fogTileRange.asSequence()
            .filter { tile -> tile.x >= 11 || tile.y >= 21 }
            .toSet()

        // When
        val actual = calculateUnexploredFogRanges(
            tileRange = fogTileRange,
            exploredTiles = exploredTiles,
        )

        // Then
        actual shouldContainExactly listOf(
            ExplorationTileRange(
                zoom = 16,
                minX = 9,
                maxX = 10,
                minY = 19,
                maxY = 20,
            ),
        )
    }

    @Test
    fun `calculateUnexploredFogRanges should merge vertical runs when adjacent rows share the same unexplored span`() {
        // Given
        val tileRange = ExplorationTileRange(
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
            tileRange = tileRange,
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
        val tileRange = ExplorationTileRange(
            zoom = 16,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )
        val exploredTiles = tileRange.asSequence().toSet()

        // When
        val actual = calculateUnexploredFogRanges(
            tileRange = tileRange,
            exploredTiles = exploredTiles,
        )

        // Then
        actual.isEmpty() shouldBe true
    }
}
