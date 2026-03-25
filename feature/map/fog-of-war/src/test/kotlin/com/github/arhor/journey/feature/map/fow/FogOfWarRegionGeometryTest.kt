package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.feature.map.fow.model.GridPoint
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.Test

class FogOfWarRegionGeometryTest {

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
        actual.size shouldBeGreaterThan loop.size
        actual.containsPointNear(x = 0.0, y = 0.0) shouldBe false
        actual.containsPointNear(x = 0.073_223_304_7, y = 0.073_223_304_7, tolerance = 1e-6) shouldBe true
        actual.containsPointNear(x = 1.073_223_304_7, y = 1.073_223_304_7, tolerance = 1e-6) shouldBe true
    }

    private fun List<GridPoint>.containsPointNear(
        x: Double,
        y: Double,
        tolerance: Double = 1e-9,
    ): Boolean = any { point ->
        kotlin.math.abs(point.x - x) <= tolerance &&
            kotlin.math.abs(point.y - y) <= tolerance
    }
}
