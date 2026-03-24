package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.feature.map.fow.model.BoundaryLoopsResult
import com.github.arhor.journey.feature.map.fow.model.DirectedEdge
import com.github.arhor.journey.feature.map.fow.model.FogOfWarGeometryMetrics
import com.github.arhor.journey.feature.map.fow.model.GridPoint
import com.github.arhor.journey.feature.map.fow.model.GridVertex
import com.github.arhor.journey.feature.map.fow.model.TileCell
import com.github.arhor.journey.feature.map.fow.model.TileRegionGeometriesBuildResult
import com.github.arhor.journey.feature.map.fow.model.TileRegionGeometry
import com.github.arhor.journey.feature.map.fow.model.TileRegionGeometryBuildResult
import com.github.arhor.journey.feature.map.fow.model.TileRegionGeometryMetrics
import com.github.arhor.journey.feature.map.fow.model.TileRegionRing
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.Position
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sinh

private const val DEFAULT_CORNER_RADIUS_TILES = 0.50
private const val DEFAULT_ARC_SEGMENTS_PER_CORNER = 10
private const val HALF_LONGITUDE_SPAN = 180.0
private const val FULL_LONGITUDE_SPAN = 360.0
private const val DEGREES_PER_RADIAN = 180.0 / PI
private const val FULL_TURN_RADIANS = PI * 2.0

internal const val GEOMETRY_EPSILON = 1e-9

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
    require(first().closeTo(last(), GEOMETRY_EPSILON)) { "A rounded loop must be closed." }

    val openLoop = dropLast(1)
    val roundedLoop = mutableListOf<GridPoint>()

    for (index in openLoop.indices) {
        checkCancelled()
        val prev = openLoop[(index - 1 + openLoop.size) % openLoop.size]
        val curr = openLoop[index]
        val next = openLoop[(index + 1) % openLoop.size]

        if (areCollinear(prev, curr, next)) {
            roundedLoop.addIfDistinct(curr)
            continue
        }

        val incoming = prev - curr
        val outgoing = next - curr
        val trimDistance = min(
            cornerRadiusTiles,
            min(incoming.length() / 2.0, outgoing.length() / 2.0),
        )

        if (trimDistance <= GEOMETRY_EPSILON) {
            roundedLoop.addIfDistinct(curr)
            continue
        }

        val entry = curr + incoming.normalized(GEOMETRY_EPSILON) * trimDistance
        val exit = curr + outgoing.normalized(GEOMETRY_EPSILON) * trimDistance

        roundedLoop.addIfDistinct(entry)
        quarterArcPoints(
            entry = entry,
            corner = curr,
            exit = exit,
            radius = trimDistance,
            arcSegmentsPerCorner = arcSegmentsPerCorner,
        ).drop(1).forEach(roundedLoop::addIfDistinct)
    }

    roundedLoop.addIfDistinct(roundedLoop.first())

    return roundedLoop
}

internal fun List<TileRegionGeometry>.toPolygonFeatureCollection(
    checkCancelled: () -> Unit = {},
): FeatureCollection<Polygon, JsonObject?> =
    FeatureCollection(
        features = mapIndexed { index, region ->
            checkCancelled()
            Feature(
                geometry = region.toPolygon(),
                properties = buildJsonObject { },
                id = JsonPrimitive("${region.zoom}#$index"),
            )
        },
    )

/* ------------------------------------------ Internal implementation ------------------------------------------- */

private fun GridPoint.toPosition(zoom: Int): Position {
    val tilesPerAxis = tilesPerAxis(zoom)
    val longitude = x / tilesPerAxis * FULL_LONGITUDE_SPAN - HALF_LONGITUDE_SPAN
    val latitude = tileYToLatitude(y, zoom)

    return Position(
        longitude = longitude,
        latitude = latitude,
    )
}

private fun TileRegionGeometry.toPolygon(): Polygon = Polygon(
    coordinates = listOf(
        outerRing.points.map { it.toPosition(zoom) },
    ) + holeRings.map { ring ->
        ring.points.map { it.toPosition(zoom) }
    },
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

private fun Set<TileCell>.toTileRegionGeometryBuildResult(
    zoom: Int,
    cornerRadiusTiles: Double,
    arcSegmentsPerCorner: Int,
    checkCancelled: () -> Unit = {},
): TileRegionGeometryBuildResult {
    val boundaryLoopResult = extractBoundaryLoops(checkCancelled = checkCancelled)
    val boundaryLoops = boundaryLoopResult.loops
    val exteriorLoop = boundaryLoops.maxBy { loop -> abs(loop.signedAreaGeo()) }
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
    val startAngle = atan2(entry.y - center.y, entry.x - center.x)
    val endAngle = atan2(exit.y - center.y, exit.x - center.x)
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
    require(first().closeTo(last(), GEOMETRY_EPSILON)) { "A canonicalized ring must be closed." }

    val openLoop = dropLast(1)
    val startIndex = openLoop.indices.minWith(
        compareBy<Int>({ openLoop[it].y }).thenBy { openLoop[it].x },
    )
    val rotated = openLoop.drop(startIndex) + openLoop.take(startIndex)

    return rotated + rotated.first()
}

private fun List<GridPoint>.reversedClosedRing(): List<GridPoint> {
    require(first().closeTo(last(), GEOMETRY_EPSILON)) { "A reversed ring must be closed." }

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
    if (lastOrNull()?.closeTo(point, GEOMETRY_EPSILON) != true) {
        add(point)
    }
}

private fun areCollinear(a: GridVertex, b: GridVertex, c: GridVertex): Boolean {
    return (a.x == b.x && b.x == c.x)
        || (a.y == b.y && b.y == c.y)
}

private fun areCollinear(a: GridPoint, b: GridPoint, c: GridPoint): Boolean {
    return (a.x.closeTo(b.x) && b.x.closeTo(c.x))
        || (a.y.closeTo(b.y) && b.y.closeTo(c.y))
}

private fun Double.closeTo(that: Double, epsilon: Double = GEOMETRY_EPSILON): Boolean {
    return abs(this - that) <= epsilon
}
