package com.github.arhor.journey.domain.model

import io.kotest.matchers.shouldBe
import org.junit.Test

class ExplorationTileRangeTest {

    @Test
    fun `expandedBy should grow the range independently on each axis`() {
        // Given
        val range = ExplorationTileRange(
            zoom = 4,
            minX = 8,
            maxX = 9,
            minY = 5,
            maxY = 6,
        )

        // When
        val actual = range.expandedBy(
            horizontalTilePadding = 2,
            verticalTilePadding = 3,
        )

        // Then
        actual shouldBe ExplorationTileRange(
            zoom = 4,
            minX = 6,
            maxX = 11,
            minY = 2,
            maxY = 9,
        )
    }

    @Test
    fun `expandedBy should clamp the range to world bounds when padding crosses tile limits`() {
        // Given
        val range = ExplorationTileRange(
            zoom = 2,
            minX = 0,
            maxX = 1,
            minY = 0,
            maxY = 1,
        )

        // When
        val actual = range.expandedBy(
            horizontalTilePadding = 5,
            verticalTilePadding = 3,
        )

        // Then
        actual shouldBe ExplorationTileRange(
            zoom = 2,
            minX = 0,
            maxX = 3,
            minY = 0,
            maxY = 3,
        )
    }
}
