package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.feature.map.fow.model.GridPoint
import com.github.arhor.journey.feature.map.fow.model.TileRegionGeometry
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.shouldBeGreaterThan
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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

    @Test
    fun `toTileRegionGeometriesBuildResult should preserve a true hole when a donut region has no ambiguous vertices`() {
        // Given
        val donutRanges = rangesForCells(
            cells = (0..2).flatMap { x ->
                (0..2).mapNotNull { y ->
                    if (x == 1 && y == 1) null else x to y
                }
            },
        )

        // When
        val actual = donutRanges.toTileRegionGeometriesBuildResult()

        // Then
        actual.metrics.resolvedAmbiguousVertexCount shouldBe 0
        actual.geometries.size shouldBe 1
        actual.geometries.single().holeRings.size shouldBe 1
        assertValidGeometry(actual.geometries.single())
    }

    @Test
    fun `toTileRegionGeometriesBuildResult should emit a valid geometry when a north-east south-west pinch creates an ambiguous vertex`() {
        // Given
        val ambiguousRanges = rangesForCells(
            cells = listOf(
                0 to 0,
                0 to 1,
                0 to 2,
                1 to 0,
                1 to 2,
                2 to 0,
                2 to 1,
            ),
        )

        // When
        val actual = ambiguousRanges.toTileRegionGeometriesBuildResult()

        // Then
        actual.metrics.resolvedAmbiguousVertexCount shouldBe 1
        actual.geometries.size shouldBe 1
        actual.geometries.single().holeRings.size shouldBe 1
        assertValidGeometry(actual.geometries.single())
    }

    @Test
    fun `toTileRegionGeometriesBuildResult should emit a valid geometry when a north-west south-east pinch creates an ambiguous vertex`() {
        // Given
        val rotatedAmbiguousRanges = rangesForCells(
            cells = rotateClockwise(
                cells = listOf(
                    0 to 0,
                    0 to 1,
                    0 to 2,
                    1 to 0,
                    1 to 2,
                    2 to 0,
                    2 to 1,
                ),
            ),
        )

        // When
        val actual = rotatedAmbiguousRanges.toTileRegionGeometriesBuildResult()

        // Then
        actual.metrics.resolvedAmbiguousVertexCount shouldBe 1
        actual.geometries.size shouldBe 1
        actual.geometries.single().holeRings.size shouldBe 1
        assertValidGeometry(actual.geometries.single())
    }

    @Test
    fun `toTileRegionGeometriesBuildResult should keep deterministic hole ordering when one component contains multiple ambiguous vertices`() {
        // Given
        val multiAmbiguousRanges = rangesForCells(
            cells = (0..4).flatMap { x ->
                (0..4).mapNotNull { y ->
                    if ((x to y) in setOf(1 to 1, 2 to 2, 3 to 3)) null else x to y
                }
            },
        )

        // When
        val actual = multiAmbiguousRanges.toTileRegionGeometriesBuildResult()

        // Then
        actual.metrics.resolvedAmbiguousVertexCount shouldBe 2
        actual.geometries.size shouldBe 1
        actual.geometries.single().holeRings.size shouldBe 3
        actual.geometries.single().holeRings.map { it.points.first() } shouldContainExactly listOf(
            GridPoint(x = 1.5, y = 1.0),
            GridPoint(x = 2.5, y = 2.0),
            GridPoint(x = 3.5, y = 3.0),
        )
        assertValidGeometry(actual.geometries.single())
    }

    private fun List<GridPoint>.containsPointNear(
        x: Double,
        y: Double,
        tolerance: Double = 1e-9,
    ): Boolean = any { point ->
        kotlin.math.abs(point.x - x) <= tolerance &&
            kotlin.math.abs(point.y - y) <= tolerance
    }

    private fun assertValidGeometry(geometry: TileRegionGeometry) {
        val outerRing = geometry.outerRing.points
        val holeRings = geometry.holeRings.map { it.points }

        outerRing.isClosed() shouldBe true
        signedAreaGeo(outerRing) shouldBeGreaterThan 0.0
        assertNoConsecutiveDuplicates(outerRing)
        assertNoSelfIntersections(outerRing)

        holeRings.forEach { holeRing ->
            holeRing.isClosed() shouldBe true
            (signedAreaGeo(holeRing) < 0.0) shouldBe true
            assertNoConsecutiveDuplicates(holeRing)
            assertNoSelfIntersections(holeRing)
            assertNoSharedPoints(outerRing, holeRing)
            assertNoIntersections(outerRing, holeRing)
            locatePoint(outerRing, findInteriorProbePoint(holeRing)) shouldBe PointLocation.INSIDE
        }

        for (leftIndex in holeRings.indices) {
            for (rightIndex in leftIndex + 1 until holeRings.size) {
                assertNoSharedPoints(holeRings[leftIndex], holeRings[rightIndex])
                assertNoIntersections(holeRings[leftIndex], holeRings[rightIndex])
            }
        }
    }

    private fun rangesForCells(
        cells: List<Pair<Int, Int>>,
        zoom: Int = 16,
    ): List<ExplorationTileRange> = cells.map { (x, y) ->
        ExplorationTileRange(
            zoom = zoom,
            minX = x,
            maxX = x,
            minY = y,
            maxY = y,
        )
    }

    private fun rotateClockwise(cells: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val maxCoordinate = cells.maxOf { max(it.first, it.second) }

        return cells.map { (x, y) ->
            maxCoordinate - y to x
        }
    }

    private fun List<GridPoint>.isClosed(): Boolean = first().closeTo(last())

    private fun signedAreaGeo(points: List<GridPoint>): Double {
        var area = 0.0

        for (index in 0 until points.lastIndex) {
            val current = points[index]
            val next = points[index + 1]
            area += next.x * current.y - current.x * next.y
        }

        return area / 2.0
    }

    private fun assertNoConsecutiveDuplicates(points: List<GridPoint>) {
        for (index in 0 until points.lastIndex) {
            points[index].closeTo(points[index + 1]) shouldBe false
        }
    }

    private fun assertNoSelfIntersections(points: List<GridPoint>) {
        val segmentCount = points.lastIndex

        for (leftIndex in 0 until segmentCount) {
            val leftStart = points[leftIndex]
            val leftEnd = points[leftIndex + 1]

            for (rightIndex in leftIndex + 1 until segmentCount) {
                if (areAdjacentSegments(leftIndex, rightIndex, segmentCount)) {
                    continue
                }

                val rightStart = points[rightIndex]
                val rightEnd = points[rightIndex + 1]

                segmentsIntersect(leftStart, leftEnd, rightStart, rightEnd) shouldBe false
            }
        }
    }

    private fun assertNoSharedPoints(
        left: List<GridPoint>,
        right: List<GridPoint>,
    ) {
        val hasSharedPoint = left.dropLast(1).any { leftPoint ->
            right.dropLast(1).any { rightPoint -> leftPoint.closeTo(rightPoint) }
        }

        hasSharedPoint shouldBe false
    }

    private fun assertNoIntersections(
        left: List<GridPoint>,
        right: List<GridPoint>,
    ) {
        for (leftIndex in 0 until left.lastIndex) {
            val leftStart = left[leftIndex]
            val leftEnd = left[leftIndex + 1]

            for (rightIndex in 0 until right.lastIndex) {
                val rightStart = right[rightIndex]
                val rightEnd = right[rightIndex + 1]

                segmentsIntersect(leftStart, leftEnd, rightStart, rightEnd) shouldBe false
            }
        }
    }

    private fun findInteriorProbePoint(points: List<GridPoint>): GridPoint {
        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            val segment = end - start
            val segmentLength = segment.length()
            if (segmentLength <= GEOMETRY_EPSILON) {
                continue
            }

            val midpoint = GridPoint(
                x = (start.x + end.x) / 2.0,
                y = (start.y + end.y) / 2.0,
            )
            val offset = min(0.05, segmentLength / 4.0)
            val normal = GridPoint(
                x = -segment.y / segmentLength * offset,
                y = segment.x / segmentLength * offset,
            )
            val candidates = listOf(
                midpoint + normal,
                midpoint - normal,
            )

            candidates.firstOrNull { candidate -> locatePoint(points, candidate) == PointLocation.INSIDE }
                ?.let { return it }
        }

        error("Failed to find an interior probe point for a test ring.")
    }

    private fun locatePoint(
        points: List<GridPoint>,
        point: GridPoint,
    ): PointLocation {
        for (index in 0 until points.lastIndex) {
            if (point.isOnSegment(points[index], points[index + 1])) {
                return PointLocation.BOUNDARY
            }
        }

        var isInside = false

        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            val intersectsRay = (start.y > point.y) != (end.y > point.y) &&
                point.x < ((end.x - start.x) * (point.y - start.y) / (end.y - start.y) + start.x)

            if (intersectsRay) {
                isInside = !isInside
            }
        }

        return if (isInside) PointLocation.INSIDE else PointLocation.OUTSIDE
    }

    private fun GridPoint.isOnSegment(
        start: GridPoint,
        end: GridPoint,
    ): Boolean {
        val cross = ((end.x - start.x) * (y - start.y)) - ((end.y - start.y) * (x - start.x))
        if (!cross.closeTo(0.0)) {
            return false
        }

        val minX = min(start.x, end.x) - GEOMETRY_EPSILON
        val maxX = max(start.x, end.x) + GEOMETRY_EPSILON
        val minY = min(start.y, end.y) - GEOMETRY_EPSILON
        val maxY = max(start.y, end.y) + GEOMETRY_EPSILON

        return x in minX..maxX && y in minY..maxY
    }

    private fun segmentsIntersect(
        startA: GridPoint,
        endA: GridPoint,
        startB: GridPoint,
        endB: GridPoint,
    ): Boolean {
        val orientation1 = orientation(startA, endA, startB)
        val orientation2 = orientation(startA, endA, endB)
        val orientation3 = orientation(startB, endB, startA)
        val orientation4 = orientation(startB, endB, endA)

        if (orientation1 * orientation2 < 0.0 && orientation3 * orientation4 < 0.0) {
            return true
        }

        return (orientation1.closeTo(0.0) && startB.isOnSegment(startA, endA)) ||
            (orientation2.closeTo(0.0) && endB.isOnSegment(startA, endA)) ||
            (orientation3.closeTo(0.0) && startA.isOnSegment(startB, endB)) ||
            (orientation4.closeTo(0.0) && endA.isOnSegment(startB, endB))
    }

    private fun orientation(
        start: GridPoint,
        end: GridPoint,
        point: GridPoint,
    ): Double = ((end.x - start.x) * (point.y - start.y)) - ((end.y - start.y) * (point.x - start.x))

    private fun areAdjacentSegments(
        leftIndex: Int,
        rightIndex: Int,
        segmentCount: Int,
    ): Boolean {
        return leftIndex == rightIndex ||
            abs(leftIndex - rightIndex) == 1 ||
            (leftIndex == 0 && rightIndex == segmentCount - 1) ||
            (rightIndex == 0 && leftIndex == segmentCount - 1)
    }

    private fun GridPoint.closeTo(
        other: GridPoint,
        epsilon: Double = GEOMETRY_EPSILON,
    ): Boolean = abs(x - other.x) <= epsilon && abs(y - other.y) <= epsilon

    private fun Double.closeTo(
        other: Double,
        epsilon: Double = GEOMETRY_EPSILON,
    ): Boolean = abs(this - other) <= epsilon

    private enum class PointLocation {
        INSIDE,
        OUTSIDE,
        BOUNDARY,
    }
}
