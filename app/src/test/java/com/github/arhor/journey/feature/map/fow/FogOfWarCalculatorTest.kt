package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.ExplorationTileRange
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test

class FogOfWarCalculatorTest {

    private val fogOfWarCalculator = FogOfWarCalculator()

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
        val actual = fogOfWarCalculator.calculateUnexploredFogRanges(
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
        val actual = fogOfWarCalculator.calculateUnexploredFogRanges(
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
            MapTile(zoom = 16, x = 1, y = 2),
        )

        // When
        val actual = fogOfWarCalculator.calculateUnexploredFogRanges(
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
        val actual = fogOfWarCalculator.calculateUnexploredFogRanges(
            tileRange = tileRange,
            exploredTiles = exploredTiles,
        )

        // Then
        actual.isEmpty() shouldBe true
    }

    @Test
    fun `calculateUnexploredFogRanges should ignore explored tiles from different zoom levels`() {
        // Given
        val tileRange = ExplorationTileRange(
            zoom = 16,
            minX = 10,
            maxX = 10,
            minY = 20,
            maxY = 20,
        )
        val exploredTiles = setOf(
            MapTile(zoom = 17, x = 10, y = 20),
        )

        // When
        val actual = fogOfWarCalculator.calculateUnexploredFogRanges(
            tileRange = tileRange,
            exploredTiles = exploredTiles,
        )

        // Then
        actual shouldContainExactly listOf(tileRange)
    }

    @Test
    fun `calculateUnexploredFogRanges should produce equivalent fog ranges when explored tiles are packed`() {
        // Given
        val tileRange = ExplorationTileRange(
            zoom = 16,
            minX = 0,
            maxX = 4,
            minY = 0,
            maxY = 3,
        )
        val exploredTiles = setOf(
            MapTile(zoom = 16, x = 0, y = 0),
            MapTile(zoom = 16, x = 1, y = 0),
            MapTile(zoom = 16, x = 1, y = 1),
            MapTile(zoom = 16, x = 3, y = 2),
            MapTile(zoom = 17, x = 3, y = 2),
        )

        // When
        val unpacked = fogOfWarCalculator.calculateUnexploredFogRanges(
            tileRange = tileRange,
            exploredTiles = exploredTiles,
        )
        val packed = fogOfWarCalculator.calculateUnexploredFogRanges(
            tileRange = tileRange,
            exploredTileKeys = exploredTiles.toPackedLongArray(),
        )

        // Then
        packed shouldContainExactly unpacked
    }

    @Test
    fun `calculateUnexploredFogRanges should normalize unsorted duplicate packed tiles when scanning fog ranges`() {
        // Given
        val tileRange = ExplorationTileRange(
            zoom = 16,
            minX = 0,
            maxX = 2,
            minY = 0,
            maxY = 1,
        )
        val exploredTileKeys = longArrayOf(
            MapTile(zoom = 16, x = 1, y = 0).packedValue,
            MapTile(zoom = 16, x = 0, y = 1).packedValue,
            MapTile(zoom = 16, x = 1, y = 0).packedValue,
        )

        // When
        val actual = fogOfWarCalculator.calculateUnexploredFogRanges(
            tileRange = tileRange,
            exploredTileKeys = exploredTileKeys,
        )

        // Then
        actual shouldContainExactly listOf(
            ExplorationTileRange(zoom = 16, minX = 0, maxX = 0, minY = 0, maxY = 0),
            ExplorationTileRange(zoom = 16, minX = 2, maxX = 2, minY = 0, maxY = 0),
            ExplorationTileRange(zoom = 16, minX = 1, maxX = 2, minY = 1, maxY = 1),
        )
    }

    @Test
    fun `calculateExploredTileRanges should merge arbitrary explored tiles into stable rectangles`() {
        // Given
        val tileRange = ExplorationTileRange(
            zoom = 16,
            minX = 0,
            maxX = 3,
            minY = 0,
            maxY = 2,
        )
        val exploredTiles = setOf(
            MapTile(zoom = 16, x = 0, y = 0),
            MapTile(zoom = 16, x = 1, y = 0),
            MapTile(zoom = 16, x = 0, y = 1),
            MapTile(zoom = 16, x = 1, y = 1),
            MapTile(zoom = 16, x = 3, y = 2),
        )

        // When
        val actual = fogOfWarCalculator.calculateExploredTileRanges(
            tileRange = tileRange,
            exploredTiles = exploredTiles,
        )

        // Then
        actual shouldContainExactly listOf(
            ExplorationTileRange(zoom = 16, minX = 0, maxX = 1, minY = 0, maxY = 1),
            ExplorationTileRange(zoom = 16, minX = 3, maxX = 3, minY = 2, maxY = 2),
        )
    }

    @Test
    fun `calculateExploredTileRanges should ignore explored tiles from different zoom levels`() {
        // Given
        val tileRange = ExplorationTileRange(
            zoom = 16,
            minX = 10,
            maxX = 10,
            minY = 20,
            maxY = 20,
        )
        val exploredTiles = setOf(
            MapTile(zoom = 17, x = 10, y = 20),
        )

        // When
        val actual = fogOfWarCalculator.calculateExploredTileRanges(
            tileRange = tileRange,
            exploredTiles = exploredTiles,
        )

        // Then
        actual shouldContainExactly emptyList()
    }

    @Test
    fun `calculateExploredTileRanges should produce equivalent explored ranges when tiles are packed`() {
        // Given
        val tileRange = ExplorationTileRange(
            zoom = 16,
            minX = 0,
            maxX = 4,
            minY = 0,
            maxY = 3,
        )
        val exploredTiles = setOf(
            MapTile(zoom = 16, x = 0, y = 0),
            MapTile(zoom = 16, x = 1, y = 0),
            MapTile(zoom = 16, x = 1, y = 1),
            MapTile(zoom = 16, x = 3, y = 2),
            MapTile(zoom = 17, x = 3, y = 2),
        )

        // When
        val unpacked = fogOfWarCalculator.calculateExploredTileRanges(
            tileRange = tileRange,
            exploredTiles = exploredTiles,
        )
        val packed = fogOfWarCalculator.calculateExploredTileRanges(
            tileRange = tileRange,
            exploredTileKeys = exploredTiles.toPackedLongArray(),
        )

        // Then
        packed shouldContainExactly unpacked
    }

    private fun Set<MapTile>.toPackedLongArray(): LongArray =
        map(MapTile::packedValue).toLongArray()
}
