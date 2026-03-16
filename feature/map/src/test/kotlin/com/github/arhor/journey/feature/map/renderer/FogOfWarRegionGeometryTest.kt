package com.github.arhor.journey.feature.map.renderer

import com.github.arhor.journey.domain.model.ExplorationTileRange
import io.kotest.matchers.shouldBe
import org.junit.Test

class FogOfWarRegionGeometryTest {

    @Test
    fun `toTileRegionGeometries should merge adjacent ranges into a single region`() {
        // Given
        val fogRanges = listOf(
            ExplorationTileRange(
                zoom = 16,
                minX = 10,
                maxX = 11,
                minY = 20,
                maxY = 20,
            ),
            ExplorationTileRange(
                zoom = 16,
                minX = 10,
                maxX = 10,
                minY = 21,
                maxY = 21,
            ),
        )

        // When
        val actual = fogRanges.toTileRegionGeometries(
            cornerRadiusTiles = 0.25,
            arcSegmentsPerCorner = 2,
        )

        // Then
        actual.size shouldBe 1
        actual.single().holeRings.isEmpty() shouldBe true
    }

    @Test
    fun `toTileRegionGeometries should keep enclosed empty spaces as hole rings`() {
        // Given
        val fogRanges = listOf(
            ExplorationTileRange(
                zoom = 16,
                minX = 0,
                maxX = 2,
                minY = 0,
                maxY = 0,
            ),
            ExplorationTileRange(
                zoom = 16,
                minX = 0,
                maxX = 0,
                minY = 1,
                maxY = 1,
            ),
            ExplorationTileRange(
                zoom = 16,
                minX = 2,
                maxX = 2,
                minY = 1,
                maxY = 1,
            ),
            ExplorationTileRange(
                zoom = 16,
                minX = 0,
                maxX = 2,
                minY = 2,
                maxY = 2,
            ),
        )

        // When
        val actual = fogRanges.toTileRegionGeometries(
            cornerRadiusTiles = 0.25,
            arcSegmentsPerCorner = 2,
        )

        // Then
        actual.size shouldBe 1
        actual.single().holeRings.size shouldBe 1
    }

    @Test
    fun `roundOrthogonalLoop should replace sharp convex and concave corners with arc points`() {
        // Given
        val loop = listOf(
            GridPoint(x = 0.0, y = 0.0),
            GridPoint(x = 2.0, y = 0.0),
            GridPoint(x = 2.0, y = 1.0),
            GridPoint(x = 1.0, y = 1.0),
            GridPoint(x = 1.0, y = 2.0),
            GridPoint(x = 0.0, y = 2.0),
            GridPoint(x = 0.0, y = 0.0),
        )

        // When
        val actual = loop.roundOrthogonalLoop(
            cornerRadiusTiles = 0.25,
            arcSegmentsPerCorner = 2,
        )

        // Then
        (actual.size > loop.size) shouldBe true
        actual.containsPointNear(x = 0.0, y = 0.0) shouldBe false
        actual.containsPointNear(x = 0.073_223_304_7, y = 0.073_223_304_7, tolerance = 1e-6) shouldBe true
        actual.containsPointNear(x = 1.073_223_304_7, y = 1.073_223_304_7, tolerance = 1e-6) shouldBe true
    }
}

private fun List<GridPoint>.containsPointNear(
    x: Double,
    y: Double,
    tolerance: Double = 1e-9,
): Boolean = any { point ->
    kotlin.math.abs(point.x - x) <= tolerance &&
        kotlin.math.abs(point.y - y) <= tolerance
}
