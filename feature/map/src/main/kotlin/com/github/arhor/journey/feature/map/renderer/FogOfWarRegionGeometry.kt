package com.github.arhor.journey.feature.map.renderer

import com.github.arhor.journey.domain.model.ExplorationTileRange
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sinh
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.Position

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

internal fun List<ExplorationTileRange>.toTileRegionGeometries(
    cornerRadiusTiles: Double = DEFAULT_CORNER_RADIUS_TILES,
    arcSegmentsPerCorner: Int = DEFAULT_ARC_SEGMENTS_PER_CORNER,
): List<TileRegionGeometry> {
    require(cornerRadiusTiles >= 0.0) { "cornerRadiusTiles must be >= 0." }
    require(arcSegmentsPerCorner >= 1) { "arcSegmentsPerCorner must be >= 1." }

    return groupBy(ExplorationTileRange::zoom)
        .toSortedMap()
        .flatMap { (zoom, ranges) ->
            ranges.toTileCells()
                .connectedComponents()
                .map { cells ->
                    cells.toTileRegionGeometry(
                        zoom = zoom,
                        cornerRadiusTiles = cornerRadiusTiles,
                        arcSegmentsPerCorner = arcSegmentsPerCorner,
                    )
                }
                .sortedWith(
                    compareBy<TileRegionGeometry> { it.outerRing.points.first().y }
                        .thenBy { it.outerRing.points.first().x },
                )
        }
}

internal fun List<GridPoint>.roundOrthogonalLoop(
    cornerRadiusTiles: Double = DEFAULT_CORNER_RADIUS_TILES,
    arcSegmentsPerCorner: Int = DEFAULT_ARC_SEGMENTS_PER_CORNER,
): List<GridPoint> {
    require(cornerRadiusTiles >= 0.0) { "cornerRadiusTiles must be >= 0." }
    require(arcSegmentsPerCorner >= 1) { "arcSegmentsPerCorner must be >= 1." }
    require(first().approximatelyEquals(last())) { "A rounded loop must be closed." }

    val openLoop = dropLast(1)
    val roundedLoop = mutableListOf<GridPoint>()

    for (index in openLoop.indices) {
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

private data class BoundaryEdgeTrace(
    val edge: DirectedEdge,
    val sourceCell: TileCell,
    val side: BoundarySide,
)

private enum class BoundarySide {
    TOP,
    RIGHT,
    BOTTOM,
    LEFT,
}

private fun List<ExplorationTileRange>.toTileCells(): Set<TileCell> {
    val cells = linkedSetOf<TileCell>()

    for (range in this) {
        for (y in range.minY..range.maxY) {
            for (x in range.minX..range.maxX) {
                cells += TileCell(x = x, y = y)
            }
        }
    }

    return cells
}

private fun Set<TileCell>.connectedComponents(): List<Set<TileCell>> {
    val remaining = toMutableSet()
    val components = mutableListOf<Set<TileCell>>()

    while (remaining.isNotEmpty()) {
        val start = remaining.minWith(compareBy<TileCell> { it.y }.thenBy { it.x })
        val queue = ArrayDeque<TileCell>()
        val component = linkedSetOf<TileCell>()

        queue.addLast(start)
        remaining.remove(start)

        while (queue.isNotEmpty()) {
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
): TileRegionGeometry {
    val boundaryLoops = extractBoundaryLoops(zoom = zoom)
    val exteriorLoop = boundaryLoops.maxBy { loop -> kotlin.math.abs(loop.signedAreaGeo()) }
    val holeLoops = boundaryLoops
        .filterNot { it === exteriorLoop }
        .sortedWith(
            compareBy<List<GridPoint>> { it.minOf(GridPoint::y) }
                .thenBy { it.minOf(GridPoint::x) },
        )

    return TileRegionGeometry(
        zoom = zoom,
        outerRing = TileRegionRing(
            exteriorLoop
                .roundOrthogonalLoop(
                    cornerRadiusTiles = cornerRadiusTiles,
                    arcSegmentsPerCorner = arcSegmentsPerCorner,
                )
                .ensureCounterClockwiseGeo()
                .canonicalizeClosedRing(),
        ),
        holeRings = holeLoops.map { holeLoop ->
            TileRegionRing(
                holeLoop
                    .roundOrthogonalLoop(
                        cornerRadiusTiles = cornerRadiusTiles,
                        arcSegmentsPerCorner = arcSegmentsPerCorner,
                    )
                    .ensureClockwiseGeo()
                    .canonicalizeClosedRing(),
            )
        },
    )
}

private fun Set<TileCell>.extractBoundaryLoops(
    zoom: Int,
): List<List<GridPoint>> {
    val edgesByStart = mutableMapOf<GridVertex, BoundaryEdgeTrace>()

    for (cell in this) {
        if (TileCell(x = cell.x, y = cell.y - 1) !in this) {
            edgesByStart.addBoundaryEdge(
                trace = BoundaryEdgeTrace(
                    edge = DirectedEdge(
                        start = GridVertex(x = cell.x, y = cell.y),
                        end = GridVertex(x = cell.x + 1, y = cell.y),
                    ),
                    sourceCell = cell,
                    side = BoundarySide.TOP,
                ),
                occupiedCells = this,
                zoom = zoom,
            )
        }

        if (TileCell(x = cell.x + 1, y = cell.y) !in this) {
            edgesByStart.addBoundaryEdge(
                trace = BoundaryEdgeTrace(
                    edge = DirectedEdge(
                        start = GridVertex(x = cell.x + 1, y = cell.y),
                        end = GridVertex(x = cell.x + 1, y = cell.y + 1),
                    ),
                    sourceCell = cell,
                    side = BoundarySide.RIGHT,
                ),
                occupiedCells = this,
                zoom = zoom,
            )
        }

        if (TileCell(x = cell.x, y = cell.y + 1) !in this) {
            edgesByStart.addBoundaryEdge(
                trace = BoundaryEdgeTrace(
                    edge = DirectedEdge(
                        start = GridVertex(x = cell.x + 1, y = cell.y + 1),
                        end = GridVertex(x = cell.x, y = cell.y + 1),
                    ),
                    sourceCell = cell,
                    side = BoundarySide.BOTTOM,
                ),
                occupiedCells = this,
                zoom = zoom,
            )
        }

        if (TileCell(x = cell.x - 1, y = cell.y) !in this) {
            edgesByStart.addBoundaryEdge(
                trace = BoundaryEdgeTrace(
                    edge = DirectedEdge(
                        start = GridVertex(x = cell.x, y = cell.y + 1),
                        end = GridVertex(x = cell.x, y = cell.y),
                    ),
                    sourceCell = cell,
                    side = BoundarySide.LEFT,
                ),
                occupiedCells = this,
                zoom = zoom,
            )
        }
    }

    val remainingStarts = edgesByStart.keys.toMutableSet()
    val loops = mutableListOf<List<GridPoint>>()

    while (remainingStarts.isNotEmpty()) {
        val firstStart = remainingStarts.minWith(compareBy<GridVertex> { it.y }.thenBy { it.x })
        val firstEdge = edgesByStart.getValue(firstStart).edge
        val vertices = mutableListOf<GridVertex>()
        var currentEdge = firstEdge

        while (true) {
            remainingStarts.remove(currentEdge.start)
            vertices += currentEdge.start

            if (currentEdge.end == firstStart) {
                vertices += currentEdge.end
                break
            }

            currentEdge = edgesByStart[currentEdge.end]
                ?.edge
                ?: error("Failed to continue fog boundary loop at ${currentEdge.end}.")
        }

        loops += vertices.simplifyOrthogonalLoop()
    }

    return loops
}

private fun MutableMap<GridVertex, BoundaryEdgeTrace>.addBoundaryEdge(
    trace: BoundaryEdgeTrace,
    occupiedCells: Set<TileCell>,
    zoom: Int,
) {
    val previous = put(trace.edge.start, trace)

    if (previous == null) {
        return
    }

    val message = occupiedCells.buildAmbiguousBoundaryMessage(
        zoom = zoom,
        vertex = trace.edge.start,
        previous = previous,
        current = trace,
    )
    throw IllegalStateException(message)
}

private fun Set<TileCell>.buildAmbiguousBoundaryMessage(
    zoom: Int,
    vertex: GridVertex,
    previous: BoundaryEdgeTrace,
    current: BoundaryEdgeTrace,
): String = buildString {
    append("Encountered an ambiguous fog boundary edge")
    append(" at ")
    append(vertex)
    append(" on zoom=")
    append(zoom)
    append(". componentBounds=")
    append(componentBoundsSummary())
    append(", componentCellCount=")
    append(size)
    append(", adjacentCells=")
    append(adjacentCellsSummary(vertex))
    append(", previousTrace=")
    append(previous)
    append(", currentTrace=")
    append(current)
    append(", localWindow=\n")
    append(localWindowAround(vertex))
}

private fun Set<TileCell>.componentBoundsSummary(): String {
    val minX = minOf(TileCell::x)
    val maxX = maxOf(TileCell::x)
    val minY = minOf(TileCell::y)
    val maxY = maxOf(TileCell::y)

    return "x=$minX..$maxX,y=$minY..$maxY"
}

private fun Set<TileCell>.adjacentCellsSummary(vertex: GridVertex): String {
    val northWest = TileCell(x = vertex.x - 1, y = vertex.y - 1) in this
    val northEast = TileCell(x = vertex.x, y = vertex.y - 1) in this
    val southWest = TileCell(x = vertex.x - 1, y = vertex.y) in this
    val southEast = TileCell(x = vertex.x, y = vertex.y) in this

    return "NW=$northWest,NE=$northEast,SW=$southWest,SE=$southEast"
}

private fun Set<TileCell>.localWindowAround(
    vertex: GridVertex,
    radius: Int = 2,
): String {
    val minX = vertex.x - radius
    val maxX = vertex.x + radius - 1
    val minY = vertex.y - radius
    val maxY = vertex.y + radius - 1

    return (minY..maxY).joinToString(separator = "\n") { y ->
        buildString {
            append(y)
            append(':')

            for (x in minX..maxX) {
                append(
                    if (TileCell(x = x, y = y) in this@localWindowAround) {
                        '#'
                    } else {
                        '.'
                    },
                )
            }
        }
    }
}

private fun List<GridVertex>.simplifyOrthogonalLoop(): List<GridPoint> {
    require(first() == last()) { "An orthogonal loop must be closed before simplification." }

    val openLoop = dropLast(1)
    val simplified = buildList {
        for (index in openLoop.indices) {
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
private const val FOG_OF_WAR_REGION_GEOMETRY_TAG = "FogOfWarRegionGeometry"
