package com.github.arhor.journey.feature.map.renderer

import com.github.arhor.journey.domain.model.ExplorationTileRange
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.Position
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sinh

internal data class TileRegionGeometry(
    val zoom: Int,
    val outerRing: TileRegionRing,
    val holeRings: List<TileRegionRing> = emptyList(),
) {
    fun toPolygon(): Polygon = Polygon(
        coordinates = listOf(
            outerRing.points.map { it.toPosition(zoom) },
        ) + holeRings.map { ring ->
            ring.points.map { it.toPosition(zoom) }
        },
    )
}

internal data class TileRegionRing(
    val points: List<GridPoint>,
) {
    init {
        require(points.size >= 4) { "A tile region ring must contain at least 4 points." }
        require(points.first().approximatelyEquals(points.last())) { "A tile region ring must be closed." }
    }
}

internal data class GridPoint(
    val x: Double,
    val y: Double,
)

internal data class TileRegionGeometriesBuildResult(
    val geometries: List<TileRegionGeometry>,
    val metrics: FogOfWarGeometryMetrics,
)

internal data class FogOfWarGeometryMetrics(
    val expandedCellCount: Long = 0,
    val connectedRegionCount: Int = 0,
    val boundaryEdgeCount: Int = 0,
    val loopCount: Int = 0,
    val ringPointCount: Int = 0,
)

internal fun List<ExplorationTileRange>.toTileRegionGeometries(
    cornerRadiusTiles: Double = DEFAULT_CORNER_RADIUS_TILES,
    arcSegmentsPerCorner: Int = DEFAULT_ARC_SEGMENTS_PER_CORNER,
    checkCancelled: () -> Unit = {},
): List<TileRegionGeometry> = toTileRegionGeometriesBuildResult(
    cornerRadiusTiles = cornerRadiusTiles,
    arcSegmentsPerCorner = arcSegmentsPerCorner,
    checkCancelled = checkCancelled,
).geometries

internal fun List<ExplorationTileRange>.toTileRegionGeometriesBuildResult(
    cornerRadiusTiles: Double = DEFAULT_CORNER_RADIUS_TILES,
    arcSegmentsPerCorner: Int = DEFAULT_ARC_SEGMENTS_PER_CORNER,
    checkCancelled: () -> Unit = {},
): TileRegionGeometriesBuildResult {
    require(cornerRadiusTiles >= 0.0) { "cornerRadiusTiles must be >= 0." }
    require(arcSegmentsPerCorner >= 1) { "arcSegmentsPerCorner must be >= 1." }

    checkCancelled()

    var expandedCellCount = 0L
    var connectedRegionCount = 0
    var boundaryEdgeCount = 0
    var loopCount = 0
    var ringPointCount = 0

    val geometries = groupBy(ExplorationTileRange::zoom)
        .toSortedMap()
        .flatMap { (zoom, ranges) ->
            checkCancelled()
            val cells = ranges.toTileCells(checkCancelled = checkCancelled)
            expandedCellCount += cells.size.toLong()
            val components = cells.connectedComponents(checkCancelled = checkCancelled)
            connectedRegionCount += components.size

            components
                .map { cells ->
                    val geometryResult = cells.toTileRegionGeometryBuildResult(
                        zoom = zoom,
                        cornerRadiusTiles = cornerRadiusTiles,
                        arcSegmentsPerCorner = arcSegmentsPerCorner,
                        checkCancelled = checkCancelled,
                    )
                    boundaryEdgeCount += geometryResult.metrics.boundaryEdgeCount
                    loopCount += geometryResult.metrics.loopCount
                    ringPointCount += geometryResult.metrics.ringPointCount
                    geometryResult.geometry
                }
                .sortedWith(
                    compareBy<TileRegionGeometry> { it.outerRing.points.first().y }
                        .thenBy { it.outerRing.points.first().x },
                )
        }

    return TileRegionGeometriesBuildResult(
        geometries = geometries,
        metrics = FogOfWarGeometryMetrics(
            expandedCellCount = expandedCellCount,
            connectedRegionCount = connectedRegionCount,
            boundaryEdgeCount = boundaryEdgeCount,
            loopCount = loopCount,
            ringPointCount = ringPointCount,
        ),
    )
}

internal fun List<GridPoint>.roundOrthogonalLoop(
    cornerRadiusTiles: Double = DEFAULT_CORNER_RADIUS_TILES,
    arcSegmentsPerCorner: Int = DEFAULT_ARC_SEGMENTS_PER_CORNER,
    checkCancelled: () -> Unit = {},
): List<GridPoint> {
    require(cornerRadiusTiles >= 0.0) { "cornerRadiusTiles must be >= 0." }
    require(arcSegmentsPerCorner >= 1) { "arcSegmentsPerCorner must be >= 1." }
    require(first().approximatelyEquals(last())) { "A rounded loop must be closed." }

    val openLoop = dropLast(1)
    val roundedLoop = mutableListOf<GridPoint>()

    for (index in openLoop.indices) {
        checkCancelled()
        val previous = openLoop[(index - 1 + openLoop.size) % openLoop.size]
        val current = openLoop[index]
        val next = openLoop[(index + 1) % openLoop.size]

        if (areCollinear(previous, current, next)) {
            roundedLoop.addIfDistinct(current)
            continue
        }

        val incoming = previous - current
        val outgoing = next - current
        val trimDistance = min(
            cornerRadiusTiles,
            min(incoming.length() / 2.0, outgoing.length() / 2.0),
        )

        if (trimDistance <= GEOMETRY_EPSILON) {
            roundedLoop.addIfDistinct(current)
            continue
        }

        val entry = current + incoming.normalized() * trimDistance
        val exit = current + outgoing.normalized() * trimDistance

        roundedLoop.addIfDistinct(entry)
        quarterArcPoints(
            entry = entry,
            corner = current,
            exit = exit,
            radius = trimDistance,
            arcSegmentsPerCorner = arcSegmentsPerCorner,
        ).drop(1).forEach(roundedLoop::addIfDistinct)
    }

    roundedLoop.addIfDistinct(roundedLoop.first())

    return roundedLoop
}

private data class TileCell(
    val x: Int,
    val y: Int,
)

private data class GridVertex(
    val x: Int,
    val y: Int,
)

private data class DirectedEdge(
    val start: GridVertex,
    val end: GridVertex,
)

private fun List<ExplorationTileRange>.toTileCells(checkCancelled: () -> Unit = {}): Set<TileCell> {
    val cells = linkedSetOf<TileCell>()

    for (range in this) {
        checkCancelled()
        for (y in range.minY..range.maxY) {
            checkCancelled()
            for (x in range.minX..range.maxX) {
                checkCancelled()
                cells += TileCell(x = x, y = y)
            }
        }
    }

    return cells
}

private fun Set<TileCell>.connectedComponents(checkCancelled: () -> Unit = {}): List<Set<TileCell>> {
    val remaining = toMutableSet()
    val components = mutableListOf<Set<TileCell>>()

    while (remaining.isNotEmpty()) {
        checkCancelled()
        val start = remaining.minWith(compareBy<TileCell> { it.y }.thenBy { it.x })
        val queue = ArrayDeque<TileCell>()
        val component = linkedSetOf<TileCell>()

        queue.addLast(start)
        remaining.remove(start)

        while (queue.isNotEmpty()) {
            checkCancelled()
            val cell = queue.removeFirst()
            component += cell

            cell.neighbors().forEach { neighbor ->
                if (remaining.remove(neighbor)) {
                    queue.addLast(neighbor)
                }
            }
        }

        components += component
    }

    return components
}

private fun TileCell.neighbors(): List<TileCell> = listOf(
    copy(y = y - 1),
    copy(x = x + 1),
    copy(y = y + 1),
    copy(x = x - 1),
)

private fun Set<TileCell>.toTileRegionGeometry(
    zoom: Int,
    cornerRadiusTiles: Double,
    arcSegmentsPerCorner: Int,
    checkCancelled: () -> Unit = {},
): TileRegionGeometry = toTileRegionGeometryBuildResult(
    zoom = zoom,
    cornerRadiusTiles = cornerRadiusTiles,
    arcSegmentsPerCorner = arcSegmentsPerCorner,
    checkCancelled = checkCancelled,
).geometry

private fun Set<TileCell>.toTileRegionGeometryBuildResult(
    zoom: Int,
    cornerRadiusTiles: Double,
    arcSegmentsPerCorner: Int,
    checkCancelled: () -> Unit = {},
): TileRegionGeometryBuildResult {
    val boundaryLoopResult = extractBoundaryLoops(checkCancelled = checkCancelled)
    val boundaryLoops = boundaryLoopResult.loops
    val exteriorLoop = boundaryLoops.maxBy { loop -> kotlin.math.abs(loop.signedAreaGeo()) }
    val holeLoops = boundaryLoops
        .filterNot { it === exteriorLoop }
        .sortedWith(
            compareBy<List<GridPoint>> { it.minOf(GridPoint::y) }
                .thenBy { it.minOf(GridPoint::x) },
        )

    val geometry = TileRegionGeometry(
        zoom = zoom,
        outerRing = TileRegionRing(
            exteriorLoop
                .roundOrthogonalLoop(
                    cornerRadiusTiles = cornerRadiusTiles,
                    arcSegmentsPerCorner = arcSegmentsPerCorner,
                    checkCancelled = checkCancelled,
                )
                .ensureCounterClockwiseGeo()
                .canonicalizeClosedRing(),
        ),
        holeRings = holeLoops.map { holeLoop ->
            checkCancelled()
            TileRegionRing(
                holeLoop
                    .roundOrthogonalLoop(
                        cornerRadiusTiles = cornerRadiusTiles,
                        arcSegmentsPerCorner = arcSegmentsPerCorner,
                        checkCancelled = checkCancelled,
                    )
                    .ensureClockwiseGeo()
                    .canonicalizeClosedRing(),
            )
        },
    )

    return TileRegionGeometryBuildResult(
        geometry = geometry,
        metrics = TileRegionGeometryMetrics(
            boundaryEdgeCount = boundaryLoopResult.boundaryEdgeCount,
            loopCount = boundaryLoops.size,
            ringPointCount = geometry.outerRing.points.size + geometry.holeRings.sumOf { it.points.size },
        ),
    )
}

private fun Set<TileCell>.extractBoundaryLoops(checkCancelled: () -> Unit = {}): BoundaryLoopsResult {
    val edgesByStart = mutableMapOf<GridVertex, DirectedEdge>()

    for (cell in this) {
        checkCancelled()
        if (TileCell(x = cell.x, y = cell.y - 1) !in this) {
            edgesByStart.addBoundaryEdge(
                start = GridVertex(x = cell.x, y = cell.y),
                end = GridVertex(x = cell.x + 1, y = cell.y),
            )
        }

        if (TileCell(x = cell.x + 1, y = cell.y) !in this) {
            edgesByStart.addBoundaryEdge(
                start = GridVertex(x = cell.x + 1, y = cell.y),
                end = GridVertex(x = cell.x + 1, y = cell.y + 1),
            )
        }

        if (TileCell(x = cell.x, y = cell.y + 1) !in this) {
            edgesByStart.addBoundaryEdge(
                start = GridVertex(x = cell.x + 1, y = cell.y + 1),
                end = GridVertex(x = cell.x, y = cell.y + 1),
            )
        }

        if (TileCell(x = cell.x - 1, y = cell.y) !in this) {
            edgesByStart.addBoundaryEdge(
                start = GridVertex(x = cell.x, y = cell.y + 1),
                end = GridVertex(x = cell.x, y = cell.y),
            )
        }
    }

    val remainingStarts = edgesByStart.keys.toMutableSet()
    val loops = mutableListOf<List<GridPoint>>()

    while (remainingStarts.isNotEmpty()) {
        checkCancelled()
        val firstStart = remainingStarts.minWith(compareBy<GridVertex> { it.y }.thenBy { it.x })
        val firstEdge = edgesByStart.getValue(firstStart)
        val vertices = mutableListOf<GridVertex>()
        var currentEdge = firstEdge

        while (true) {
            checkCancelled()
            remainingStarts.remove(currentEdge.start)
            vertices += currentEdge.start

            if (currentEdge.end == firstStart) {
                vertices += currentEdge.end
                break
            }

            currentEdge = edgesByStart[currentEdge.end]
                ?: error("Failed to continue fog boundary loop at ${currentEdge.end}.")
        }

        loops += vertices.simplifyOrthogonalLoop()
    }

    return BoundaryLoopsResult(
        loops = loops,
        boundaryEdgeCount = edgesByStart.size,
    )
}

private data class TileRegionGeometryBuildResult(
    val geometry: TileRegionGeometry,
    val metrics: TileRegionGeometryMetrics,
)

private data class TileRegionGeometryMetrics(
    val boundaryEdgeCount: Int,
    val loopCount: Int,
    val ringPointCount: Int,
)

private data class BoundaryLoopsResult(
    val loops: List<List<GridPoint>>,
    val boundaryEdgeCount: Int,
)

private fun MutableMap<GridVertex, DirectedEdge>.addBoundaryEdge(
    start: GridVertex,
    end: GridVertex,
) {
    val previous = put(start, DirectedEdge(start = start, end = end))
    check(previous == null) { "Encountered an ambiguous fog boundary edge at $start." }
}

private fun List<GridVertex>.simplifyOrthogonalLoop(checkCancelled: () -> Unit = {}): List<GridPoint> {
    require(first() == last()) { "An orthogonal loop must be closed before simplification." }

    val openLoop = dropLast(1)
    val simplified = buildList {
        for (index in openLoop.indices) {
            checkCancelled()
            val previous = openLoop[(index - 1 + openLoop.size) % openLoop.size]
            val current = openLoop[index]
            val next = openLoop[(index + 1) % openLoop.size]

            if (!areCollinear(previous, current, next)) {
                add(GridPoint(x = current.x.toDouble(), y = current.y.toDouble()))
            }
        }
    }

    return simplified + simplified.first()
}

private fun quarterArcPoints(
    entry: GridPoint,
    corner: GridPoint,
    exit: GridPoint,
    radius: Double,
    arcSegmentsPerCorner: Int,
): List<GridPoint> {
    val center = GridPoint(
        x = entry.x + exit.x - corner.x,
        y = entry.y + exit.y - corner.y,
    )
    val startAngle = kotlin.math.atan2(entry.y - center.y, entry.x - center.x)
    val endAngle = kotlin.math.atan2(exit.y - center.y, exit.x - center.x)
    val angleDelta = shortestAngleDelta(startAngle, endAngle)

    return (0..arcSegmentsPerCorner).map { step ->
        val angle = startAngle + angleDelta * step / arcSegmentsPerCorner

        GridPoint(
            x = center.x + cos(angle) * radius,
            y = center.y + sin(angle) * radius,
        )
    }
}

private fun shortestAngleDelta(
    startAngle: Double,
    endAngle: Double,
): Double {
    var delta = endAngle - startAngle

    while (delta <= -PI) {
        delta += FULL_TURN_RADIANS
    }

    while (delta > PI) {
        delta -= FULL_TURN_RADIANS
    }

    return delta
}

private fun List<GridPoint>.ensureCounterClockwiseGeo(): List<GridPoint> =
    if (signedAreaGeo() > 0.0) this else reversedClosedRing()

private fun List<GridPoint>.ensureClockwiseGeo(): List<GridPoint> =
    if (signedAreaGeo() < 0.0) this else reversedClosedRing()

private fun List<GridPoint>.canonicalizeClosedRing(): List<GridPoint> {
    require(first().approximatelyEquals(last())) { "A canonicalized ring must be closed." }

    val openLoop = dropLast(1)
    val startIndex = openLoop.indices.minWith(
        compareBy<Int>({ openLoop[it].y }).thenBy { openLoop[it].x },
    )
    val rotated = openLoop.drop(startIndex) + openLoop.take(startIndex)

    return rotated + rotated.first()
}

private fun List<GridPoint>.reversedClosedRing(): List<GridPoint> {
    require(first().approximatelyEquals(last())) { "A reversed ring must be closed." }

    val reversed = dropLast(1).asReversed()

    return reversed + reversed.first()
}

private fun List<GridPoint>.signedAreaGeo(): Double {
    require(size >= 4) { "A closed ring must contain at least 4 points." }

    var area = 0.0

    for (index in 0 until lastIndex) {
        val current = this[index]
        val next = this[index + 1]

        area += next.x * current.y - current.x * next.y
    }

    return area / 2.0
}

private fun GridPoint.toPosition(zoom: Int): Position {
    val tilesPerAxis = tilesPerAxis(zoom)
    val longitude = x / tilesPerAxis * FULL_LONGITUDE_SPAN - HALF_LONGITUDE_SPAN
    val latitude = tileYToLatitude(y, zoom)

    return Position(
        longitude = longitude,
        latitude = latitude,
    )
}

private fun tileYToLatitude(
    tileY: Double,
    zoom: Int,
): Double {
    val tilesPerAxis = tilesPerAxis(zoom)
    val mercator = PI * (1.0 - 2.0 * tileY / tilesPerAxis)

    return atan(sinh(mercator)) * DEGREES_PER_RADIAN
}

private fun tilesPerAxis(zoom: Int): Double = (1 shl zoom).toDouble()

private fun MutableList<GridPoint>.addIfDistinct(point: GridPoint) {
    if (lastOrNull()?.approximatelyEquals(point) != true) {
        add(point)
    }
}

private fun GridPoint.length(): Double = hypot(x, y)

private fun GridPoint.normalized(): GridPoint {
    val length = length()
    check(length > GEOMETRY_EPSILON) { "Cannot normalize a zero-length vector." }

    return this * (1.0 / length)
}

private fun GridPoint.approximatelyEquals(
    other: GridPoint,
    epsilon: Double = GEOMETRY_EPSILON,
): Boolean = kotlin.math.abs(x - other.x) <= epsilon &&
    kotlin.math.abs(y - other.y) <= epsilon

private fun areCollinear(
    previous: GridVertex,
    current: GridVertex,
    next: GridVertex,
): Boolean = (previous.x == current.x && current.x == next.x) ||
    (previous.y == current.y && current.y == next.y)

private fun areCollinear(
    previous: GridPoint,
    current: GridPoint,
    next: GridPoint,
): Boolean = (previous.x.approximatelyEquals(current.x) && current.x.approximatelyEquals(next.x)) ||
    (previous.y.approximatelyEquals(current.y) && current.y.approximatelyEquals(next.y))

private fun Double.approximatelyEquals(
    other: Double,
    epsilon: Double = GEOMETRY_EPSILON,
): Boolean = kotlin.math.abs(this - other) <= epsilon

private operator fun GridPoint.plus(other: GridPoint): GridPoint = GridPoint(
    x = x + other.x,
    y = y + other.y,
)

private operator fun GridPoint.minus(other: GridPoint): GridPoint = GridPoint(
    x = x - other.x,
    y = y - other.y,
)

private operator fun GridPoint.times(scale: Double): GridPoint = GridPoint(
    x = x * scale,
    y = y * scale,
)

private const val DEFAULT_CORNER_RADIUS_TILES = 0.50
private const val DEFAULT_ARC_SEGMENTS_PER_CORNER = 10
private const val GEOMETRY_EPSILON = 1e-9
private const val HALF_LONGITUDE_SPAN = 180.0
private const val FULL_LONGITUDE_SPAN = 360.0
private const val DEGREES_PER_RADIAN = 180.0 / PI
private const val FULL_TURN_RADIANS = PI * 2.0
