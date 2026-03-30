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
private const val REPRESENTATIVE_POINT_OFFSET_TILES = 0.05

internal const val GEOMETRY_EPSILON = 1e-9

internal fun List<ExplorationTileRange>.toTileRegionGeometriesBuildResult(
    cornerRadiusTiles: Double = DEFAULT_CORNER_RADIUS_TILES,
    arcSegmentsPerCorner: Int = DEFAULT_ARC_SEGMENTS_PER_CORNER,
): TileRegionGeometriesBuildResult {
    require(cornerRadiusTiles >= 0.0) { "cornerRadiusTiles must be >= 0." }
    require(arcSegmentsPerCorner >= 1) { "arcSegmentsPerCorner must be >= 1." }


    var expandedCellCount = 0L
    var connectedRegionCount = 0
    var boundaryEdgeCount = 0
    var loopCount = 0
    var ringPointCount = 0
    var resolvedAmbiguousVertexCount = 0

    val geometries = groupBy(ExplorationTileRange::zoom)
        .toSortedMap()
        .flatMap { (zoom, ranges) ->
            val cells = ranges.toTileCells()
            expandedCellCount += cells.size.toLong()
            val components = cells.connectedComponents()
            connectedRegionCount += components.size

            components
                .map { cells ->
                    val geometryResult = cells.toTileRegionGeometryBuildResult(
                        zoom = zoom,
                        cornerRadiusTiles = cornerRadiusTiles,
                        arcSegmentsPerCorner = arcSegmentsPerCorner,
                    )
                    boundaryEdgeCount += geometryResult.metrics.boundaryEdgeCount
                    loopCount += geometryResult.metrics.loopCount
                    ringPointCount += geometryResult.metrics.ringPointCount
                    resolvedAmbiguousVertexCount += geometryResult.metrics.resolvedAmbiguousVertexCount
                    geometryResult.geometries
                }
                .flatten()
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
            resolvedAmbiguousVertexCount = resolvedAmbiguousVertexCount,
        ),
    )
}

internal fun List<GridPoint>.roundOrthogonalLoop(
    cornerRadiusTiles: Double = DEFAULT_CORNER_RADIUS_TILES,
    arcSegmentsPerCorner: Int = DEFAULT_ARC_SEGMENTS_PER_CORNER,
): List<GridPoint> {
    require(cornerRadiusTiles >= 0.0) { "cornerRadiusTiles must be >= 0." }
    require(arcSegmentsPerCorner >= 1) { "arcSegmentsPerCorner must be >= 1." }
    require(first().closeTo(last(), GEOMETRY_EPSILON)) { "A rounded loop must be closed." }

    val openLoop = dropLast(1)
    val roundedLoop = mutableListOf<GridPoint>()

    for (index in openLoop.indices) {
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

internal fun List<TileRegionGeometry>.toPolygonFeatureCollection(): FeatureCollection<Polygon, JsonObject?> =
    FeatureCollection(
        features = mapIndexed { index, region ->
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

private fun Set<TileCell>.toTileRegionGeometryBuildResult(
    zoom: Int,
    cornerRadiusTiles: Double,
    arcSegmentsPerCorner: Int,
): TileRegionGeometryBuildResult {
    val boundaryLoopResult = extractBoundaryLoops()
    val boundaryLoops = boundaryLoopResult.loops.map { loop ->
        loop.validateClosedRing(label = "Orthogonal fog boundary loop")
        LoopDescriptor(
            points = loop,
            representativePoint = loop.findInteriorProbePoint(),
            sortKey = loop.minimumPoint(),
        )
    }
    val containingLoops = boundaryLoops.associateWith { loop ->
        boundaryLoops.filter { candidate ->
            candidate !== loop && candidate.points.locatePoint(loop.representativePoint) == PointLocation.INSIDE
        }
    }
    val outerLoops = boundaryLoops
        .filter { containingLoops.getValue(it).isEmpty() }
        .sortedWith(compareBy<LoopDescriptor> { it.sortKey.y }.thenBy { it.sortKey.x })
    val holeLoopsByOuter = boundaryLoops
        .filter { containingLoops.getValue(it).size == 1 }
        .groupBy { hole ->
            outerLoops
                .filter { outer -> outer.points.locatePoint(hole.representativePoint) == PointLocation.INSIDE }
                .minByOrNull { abs(it.points.signedAreaGeo()) }
                ?: error("Failed to assign fog boundary hole to an outer loop.")
        }

    boundaryLoops.forEach { loop ->
        val containingCount = containingLoops.getValue(loop).size
        check(containingCount <= 1) {
            "Nested fog boundary loops are unsupported for ${loop.sortKey}."
        }
    }

    val strictValidation = boundaryLoopResult.resolvedAmbiguousVertexCount > 0 || holeLoopsByOuter.isNotEmpty()
    val geometries = outerLoops.map { outerLoop ->
        val outerRing = TileRegionRing(
            outerLoop.points
                .roundOrthogonalLoop(cornerRadiusTiles, arcSegmentsPerCorner)
                .ensureCounterClockwiseGeo()
                .canonicalizeClosedRing(),
        )
        val holeRings = holeLoopsByOuter[outerLoop]
            .orEmpty()
            .sortedWith(compareBy<LoopDescriptor> { it.sortKey.y }.thenBy { it.sortKey.x })
            .map { loop ->
                TileRegionRing(
                    loop.points
                        .roundOrthogonalLoop(cornerRadiusTiles, arcSegmentsPerCorner)
                        .ensureClockwiseGeo()
                        .canonicalizeClosedRing(),
                )
            }
        TileRegionGeometry(
            zoom = zoom,
            outerRing = outerRing,
            holeRings = holeRings,
        ).also { geometry ->
            geometry.validate(strict = strictValidation)
        }
    }

    return TileRegionGeometryBuildResult(
        geometries = geometries,
        metrics = TileRegionGeometryMetrics(
            boundaryEdgeCount = boundaryLoopResult.boundaryEdgeCount,
            loopCount = boundaryLoops.size,
            ringPointCount = geometries.sumOf { geometry ->
                geometry.outerRing.points.size + geometry.holeRings.sumOf { it.points.size }
            },
            resolvedAmbiguousVertexCount = boundaryLoopResult.resolvedAmbiguousVertexCount,
        ),
    )
}

private fun Set<TileCell>.extractBoundaryLoops(): BoundaryLoopsResult {
    val edgesByStart = mutableMapOf<GridVertex, MutableList<DirectedEdge>>()

    for (cell in this) {
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

    val remainingEdges = edgesByStart.values
        .flatten()
        .toMutableSet()
    val loops = mutableListOf<List<GridPoint>>()
    val resolvedAmbiguousVertices = mutableSetOf<GridVertex>()

    while (remainingEdges.isNotEmpty()) {
        val firstEdge = remainingEdges.minWith(
            compareBy<DirectedEdge> { it.start.y }
                .thenBy { it.start.x }
                .thenBy { it.end.y }
                .thenBy { it.end.x },
        )
        val vertices = mutableListOf<GridVertex>()
        var currentEdge = firstEdge

        while (true) {
            check(remainingEdges.remove(currentEdge)) {
                "Encountered a duplicate fog boundary traversal at ${currentEdge.start}."
            }
            vertices += currentEdge.start

            if (currentEdge.end == firstEdge.start) {
                vertices += currentEdge.end
                break
            }

            val nextEdges = edgesByStart[currentEdge.end]
                .orEmpty()
                .filter(remainingEdges::contains)
            if (nextEdges.size == 2) {
                resolvedAmbiguousVertices += currentEdge.end
            }

            currentEdge = resolveNextBoundaryEdge(
                currentEdge = currentEdge,
                nextEdges = nextEdges,
            )
        }

        loops += vertices.simplifyOrthogonalLoop()
    }

    return BoundaryLoopsResult(
        loops = loops,
        boundaryEdgeCount = edgesByStart.values.sumOf(List<DirectedEdge>::size),
        resolvedAmbiguousVertexCount = resolvedAmbiguousVertices.size,
    )
}

private fun Set<TileCell>.resolveNextBoundaryEdge(
    currentEdge: DirectedEdge,
    nextEdges: List<DirectedEdge>,
): DirectedEdge {
    return when (nextEdges.size) {
        0 -> error("Failed to continue fog boundary loop at ${currentEdge.end}.")
        1 -> nextEdges.single()
        2 -> {
            check(hasAlternatingOccupancy(vertex = currentEdge.end)) {
                "Encountered an unsupported ambiguous fog boundary edge at ${currentEdge.end}."
            }
            val desiredDirection = currentEdge.direction().turnLeft()
            nextEdges.firstOrNull { it.direction() == desiredDirection }
                ?: error("Failed to resolve fog boundary loop at ${currentEdge.end}.")
        }
        else -> error("Encountered an unsupported fog boundary degree at ${currentEdge.end}.")
    }
}

private fun Set<TileCell>.hasAlternatingOccupancy(vertex: GridVertex): Boolean {
    val northWest = TileCell(x = vertex.x - 1, y = vertex.y - 1) in this
    val northEast = TileCell(x = vertex.x, y = vertex.y - 1) in this
    val southEast = TileCell(x = vertex.x, y = vertex.y) in this
    val southWest = TileCell(x = vertex.x - 1, y = vertex.y) in this

    return (northWest && southEast && !northEast && !southWest)
        || (northEast && southWest && !northWest && !southEast)
}

private fun MutableMap<GridVertex, MutableList<DirectedEdge>>.addBoundaryEdge(
    start: GridVertex,
    end: GridVertex,
) {
    getOrPut(start) { mutableListOf() }
        .add(DirectedEdge(start = start, end = end))
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

private fun TileRegionGeometry.validate(strict: Boolean) {
    outerRing.points.validateClosedRing(label = "Fog outer ring", validateSelfIntersection = strict)
    holeRings.forEachIndexed { index, ring ->
        ring.points.validateClosedRing(label = "Fog hole ring #$index", validateSelfIntersection = strict)
    }

    if (!strict) {
        return
    }

    holeRings.forEachIndexed { index, holeRing ->
        check(!outerRing.points.sharesPointsWith(holeRing.points)) {
            "Fog hole ring #$index still touches the outer ring after smoothing."
        }
        check(!outerRing.points.intersectsWith(holeRing.points)) {
            "Fog hole ring #$index intersects the outer ring after smoothing."
        }
        val representativePoint = holeRing.points.findInteriorProbePoint()
        check(outerRing.points.locatePoint(representativePoint) == PointLocation.INSIDE) {
            "Fog hole ring #$index is not strictly inside the outer ring."
        }
    }

    for (leftIndex in holeRings.indices) {
        for (rightIndex in leftIndex + 1 until holeRings.size) {
            val left = holeRings[leftIndex].points
            val right = holeRings[rightIndex].points
            check(!left.sharesPointsWith(right)) {
                "Fog hole rings #$leftIndex and #$rightIndex share a boundary point."
            }
            check(!left.intersectsWith(right)) {
                "Fog hole rings #$leftIndex and #$rightIndex intersect."
            }
        }
    }
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

private fun List<GridPoint>.validateClosedRing(
    label: String,
    validateSelfIntersection: Boolean = true,
) {
    require(size >= 4) { "$label must contain at least 4 points." }
    require(first().closeTo(last(), GEOMETRY_EPSILON)) { "$label must be closed." }

    for (index in 0 until lastIndex) {
        check(!this[index].closeTo(this[index + 1], GEOMETRY_EPSILON)) {
            "$label contains consecutive duplicate points at index $index."
        }
    }

    if (validateSelfIntersection) {
        validateNoSelfIntersections(label = label)
    }
}

private fun List<GridPoint>.validateNoSelfIntersections(label: String) {
    val segmentCount = lastIndex

    for (leftIndex in 0 until segmentCount) {
        val leftStart = this[leftIndex]
        val leftEnd = this[leftIndex + 1]

        for (rightIndex in leftIndex + 1 until segmentCount) {
            if (areAdjacentSegments(leftIndex, rightIndex, segmentCount)) {
                continue
            }

            val rightStart = this[rightIndex]
            val rightEnd = this[rightIndex + 1]

            check(!segmentsIntersect(leftStart, leftEnd, rightStart, rightEnd)) {
                "$label intersects itself between segments $leftIndex and $rightIndex."
            }
        }
    }
}

private fun List<GridPoint>.findInteriorProbePoint(): GridPoint {
    for (index in 0 until lastIndex) {
        val start = this[index]
        val end = this[index + 1]
        val segment = end - start
        val segmentLength = segment.length()
        if (segmentLength <= GEOMETRY_EPSILON) {
            continue
        }

        val midpoint = GridPoint(
            x = (start.x + end.x) / 2.0,
            y = (start.y + end.y) / 2.0,
        )
        val offset = min(REPRESENTATIVE_POINT_OFFSET_TILES, segmentLength / 4.0)
        val normal = GridPoint(
            x = -segment.y / segmentLength * offset,
            y = segment.x / segmentLength * offset,
        )
        val candidates = listOf(
            midpoint + normal,
            midpoint - normal,
        )

        candidates.firstOrNull { candidate -> locatePoint(candidate) == PointLocation.INSIDE }
            ?.let { return it }
    }

    error("Failed to find an interior probe point for a fog boundary loop.")
}

private fun List<GridPoint>.locatePoint(point: GridPoint): PointLocation {
    for (index in 0 until lastIndex) {
        if (point.isOnSegment(start = this[index], end = this[index + 1])) {
            return PointLocation.BOUNDARY
        }
    }

    var isInside = false

    for (index in 0 until lastIndex) {
        val start = this[index]
        val end = this[index + 1]
        val intersectsRay = (start.y > point.y) != (end.y > point.y) &&
            point.x < ((end.x - start.x) * (point.y - start.y) / (end.y - start.y) + start.x)

        if (intersectsRay) {
            isInside = !isInside
        }
    }

    return if (isInside) PointLocation.INSIDE else PointLocation.OUTSIDE
}

private fun List<GridPoint>.sharesPointsWith(other: List<GridPoint>): Boolean {
    return dropLast(1).any { point ->
        other.dropLast(1).any { candidate -> point.closeTo(candidate, GEOMETRY_EPSILON) }
    }
}

private fun List<GridPoint>.intersectsWith(other: List<GridPoint>): Boolean {
    for (leftIndex in 0 until lastIndex) {
        val leftStart = this[leftIndex]
        val leftEnd = this[leftIndex + 1]

        for (rightIndex in 0 until other.lastIndex) {
            val rightStart = other[rightIndex]
            val rightEnd = other[rightIndex + 1]

            if (segmentsIntersect(leftStart, leftEnd, rightStart, rightEnd)) {
                return true
            }
        }
    }

    return false
}

private fun GridPoint.isOnSegment(
    start: GridPoint,
    end: GridPoint,
): Boolean {
    val cross = ((end.x - start.x) * (y - start.y)) - ((end.y - start.y) * (x - start.x))
    if (!cross.closeTo(0.0, GEOMETRY_EPSILON)) {
        return false
    }

    val minX = min(start.x, end.x) - GEOMETRY_EPSILON
    val maxX = kotlin.math.max(start.x, end.x) + GEOMETRY_EPSILON
    val minY = min(start.y, end.y) - GEOMETRY_EPSILON
    val maxY = kotlin.math.max(start.y, end.y) + GEOMETRY_EPSILON

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

    return (orientation1.closeTo(0.0, GEOMETRY_EPSILON) && startB.isOnSegment(startA, endA)) ||
        (orientation2.closeTo(0.0, GEOMETRY_EPSILON) && endB.isOnSegment(startA, endA)) ||
        (orientation3.closeTo(0.0, GEOMETRY_EPSILON) && startA.isOnSegment(startB, endB)) ||
        (orientation4.closeTo(0.0, GEOMETRY_EPSILON) && endA.isOnSegment(startB, endB))
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

private fun List<GridPoint>.minimumPoint(): GridPoint {
    val openLoop = dropLast(1)

    return openLoop.minWith(compareBy<GridPoint> { it.y }.thenBy { it.x })
}

private fun DirectedEdge.direction(): EdgeDirection {
    return when {
        end.x > start.x -> EdgeDirection.EAST
        end.x < start.x -> EdgeDirection.WEST
        end.y > start.y -> EdgeDirection.SOUTH
        else -> EdgeDirection.NORTH
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

private data class LoopDescriptor(
    val points: List<GridPoint>,
    val representativePoint: GridPoint,
    val sortKey: GridPoint,
)

private enum class PointLocation {
    INSIDE,
    OUTSIDE,
    BOUNDARY,
}

private enum class EdgeDirection {
    NORTH,
    EAST,
    SOUTH,
    WEST,
    ;

    fun turnLeft(): EdgeDirection = when (this) {
        NORTH -> WEST
        EAST -> NORTH
        SOUTH -> EAST
        WEST -> SOUTH
    }
}
