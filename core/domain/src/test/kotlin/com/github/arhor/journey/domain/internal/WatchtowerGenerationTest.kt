package com.github.arhor.journey.domain.internal

import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.MapTile
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class WatchtowerGenerationTest {

    @Test
    fun `definitionsInBounds should return stable ids positions and ordering when queried repeatedly`() {
        // Given
        val occupiedCell = occupiedCell()
        val queryBounds = bounds(
            ExplorationTileRange(
                zoom = WatchtowerGeneration.GENERATOR_TILE_ZOOM,
                minX = occupiedCell.x,
                maxX = occupiedCell.x + 2,
                minY = occupiedCell.y,
                maxY = occupiedCell.y + 2,
            ),
        )

        // When
        val first = WatchtowerGeneration.definitionsInBounds(queryBounds)
        val second = WatchtowerGeneration.definitionsInBounds(queryBounds)

        // Then
        first shouldBe second
        first.map { it.id } shouldBe first.map { it.id }.sorted()
    }

    @Test
    fun `definitionForId should regenerate the same definition when id is valid and occupied`() {
        // Given
        val definition = occupiedDefinition()

        // When
        val actual = WatchtowerGeneration.definitionForId(definition.id)

        // Then
        actual shouldBe definition
    }

    @Test
    fun `definitionForId should return null when id is malformed or the cell is unoccupied`() {
        // Given
        val unoccupiedCell = unoccupiedCell()

        // When
        val malformed = WatchtowerGeneration.definitionForId("watchtower:legacy:gdansk")
        val negativeCoordinate = WatchtowerGeneration.definitionForId("watchtower:v1:15:-1:10")
        val oversizedCoordinate = WatchtowerGeneration.definitionForId("watchtower:v1:15:40000:10")
        val unoccupied = WatchtowerGeneration.definitionForId(
            "watchtower:v${WatchtowerGeneration.GENERATOR_VERSION}:" +
                "${WatchtowerGeneration.GENERATOR_TILE_ZOOM}:${unoccupiedCell.x}:${unoccupiedCell.y}",
        )

        // Then
        malformed.shouldBeNull()
        negativeCoordinate.shouldBeNull()
        oversizedCoordinate.shouldBeNull()
        unoccupied.shouldBeNull()
    }

    @Test
    fun `definitionsInBounds should stay bounded and contain no duplicate ids for representative areas`() {
        // Given
        val queryBounds = GeoBounds(
            south = 54.18,
            west = 18.32,
            north = 54.48,
            east = 18.88,
        )
        val cellRange = tileRange(
            bounds = queryBounds,
            zoom = WatchtowerGeneration.GENERATOR_TILE_ZOOM,
        )

        // When
        val actual = WatchtowerGeneration.definitionsInBounds(queryBounds)

        // Then
        actual.map { it.id }.distinct().size shouldBe actual.size
        (actual.size <= cellRange.tileCount.toInt()) shouldBe true
    }

    @Test
    fun `intersectingGeneratorRanges should expand parent tiles and collapse child tiles consistently`() {
        // Given
        val occupiedCell = occupiedCell()
        val parentTile = MapTile(
            zoom = WatchtowerGeneration.GENERATOR_TILE_ZOOM - 1,
            x = occupiedCell.x shr 1,
            y = occupiedCell.y shr 1,
        )
        val childTile = MapTile(
            zoom = WatchtowerGeneration.GENERATOR_TILE_ZOOM + 1,
            x = occupiedCell.x shl 1,
            y = occupiedCell.y shl 1,
        )

        // When
        val actual = WatchtowerGeneration.intersectingGeneratorRanges(
            tiles = setOf(
                occupiedCell,
                parentTile,
                childTile,
            ),
        )

        // Then
        actual shouldHaveSize 2
        actual.any { range -> range.contains(occupiedCell) } shouldBe true
        actual.any { range -> range.tileCount == 4L } shouldBe true
    }

    @Test
    fun `intersectingGeneratorRanges should map very high zoom tiles without shift wrapping`() {
        // Given
        val highZoomTile = MapTile(
            zoom = 47,
            x = 1,
            y = 1,
        )

        // When
        val actual = WatchtowerGeneration.intersectingGeneratorRanges(setOf(highZoomTile))

        // Then
        actual shouldBe listOf(
            ExplorationTileRange(
                zoom = WatchtowerGeneration.GENERATOR_TILE_ZOOM,
                minX = 0,
                maxX = 0,
                minY = 0,
                maxY = 0,
            ),
        )
    }

    private fun occupiedDefinition() = WatchtowerGeneration.definitionForCell(occupiedCell())
        ?: error("Expected to find an occupied watchtower cell")

    private fun occupiedCell(): MapTile =
        searchCells { cell ->
            WatchtowerGeneration.definitionForCell(cell) != null
        }

    private fun unoccupiedCell(): MapTile =
        searchCells { cell ->
            WatchtowerGeneration.definitionForCell(cell) == null
        }

    private fun searchCells(predicate: (MapTile) -> Boolean): MapTile {
        for (y in 10_000..10_128) {
            for (x in 10_000..10_128) {
                val cell = MapTile(
                    zoom = WatchtowerGeneration.GENERATOR_TILE_ZOOM,
                    x = x,
                    y = y,
                )
                if (predicate(cell)) {
                    return cell
                }
            }
        }

        error("Expected to find a watchtower generator cell in the test search window")
    }
}
