package com.github.arhor.journey.domain.internal

import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.WatchtowerDefinition

object WatchtowerGeneration {
    const val GENERATOR_VERSION = 1
    const val GENERATOR_TILE_ZOOM = 15
    const val OCCUPANCY_THRESHOLD_PERCENT = 32
    const val CELL_PADDING_FRACTION = 0.22
    const val INTERACTION_RADIUS_METERS = 25.0

    private const val HASH_FRACTION_GRANULARITY = 10_000.0
    private const val ID_PREFIX = "watchtower"
    private const val DESCRIPTION = "A dormant observation relay waiting to be restored."
    private const val MAX_PARENT_SHIFT = Int.SIZE_BITS - 1

    private val adjectiveParts = listOf(
        "Silent",
        "North",
        "High",
        "Stone",
        "Far",
        "Ember",
        "Horizon",
        "Iron",
    )

    private val nounParts = listOf(
        "Relay",
        "Spire",
        "Bastion",
        "Lookout",
        "Tower",
        "Crown",
        "Perch",
        "Roost",
    )

    fun definitionsInBounds(bounds: GeoBounds): List<WatchtowerDefinition> =
        definitionsInRange(
            range = tileRange(
                bounds = bounds,
                zoom = GENERATOR_TILE_ZOOM,
            ),
        ).filter { definition ->
            bounds.contains(definition.location)
        }

    fun definitionsInRange(range: ExplorationTileRange): List<WatchtowerDefinition> =
        definitionSequenceInRange(range)
            .sortedBy(WatchtowerDefinition::id)
            .toList()

    fun definitionsForCells(cells: Collection<MapTile>): List<WatchtowerDefinition> =
        cells.asSequence()
            .mapNotNull(::definitionForCell)
            .sortedBy(WatchtowerDefinition::id)
            .toList()

    fun definitionSequenceInRange(range: ExplorationTileRange): Sequence<WatchtowerDefinition> =
        range.asSequence()
            .mapNotNull(::definitionForCell)

    fun definitionForId(id: String): WatchtowerDefinition? {
        val descriptor = parseId(id) ?: return null

        return definitionForCell(
            cell = MapTile(
                zoom = GENERATOR_TILE_ZOOM,
                x = descriptor.x,
                y = descriptor.y,
            ),
        )?.takeIf { definition ->
            definition.id == id
        }
    }

    fun intersectingGeneratorRanges(tiles: Set<MapTile>): List<ExplorationTileRange> =
        buildSet {
            tiles.forEach { tile ->
                generatorRangeForTile(tile)?.let(::add)
            }
        }.sortedWith(
            compareBy(
                ExplorationTileRange::zoom,
                ExplorationTileRange::minY,
                ExplorationTileRange::minX,
                ExplorationTileRange::maxY,
                ExplorationTileRange::maxX,
            ),
        )

    fun definitionForCell(cell: MapTile): WatchtowerDefinition? {
        if (cell.zoom != GENERATOR_TILE_ZOOM || !isOccupied(cell.x, cell.y)) {
            return null
        }

        val cellBounds = bounds(cell)
        val latitude = interpolatePadded(
            min = cellBounds.south,
            max = cellBounds.north,
            fraction = stableFraction(seed = "watchtower:v$GENERATOR_VERSION:lat:${cell.x}:${cell.y}"),
        )
        val longitude = interpolatePadded(
            min = cellBounds.west,
            max = cellBounds.east,
            fraction = stableFraction(seed = "watchtower:v$GENERATOR_VERSION:lon:${cell.x}:${cell.y}"),
        )

        return WatchtowerDefinition(
            id = buildId(cell.x, cell.y),
            name = buildName(cell.x, cell.y),
            description = DESCRIPTION,
            location = GeoPoint(
                lat = latitude,
                lon = longitude,
            ),
            interactionRadiusMeters = INTERACTION_RADIUS_METERS,
        )
    }

    private fun isOccupied(
        x: Int,
        y: Int,
    ): Boolean = stablePositiveHash(seed = "watchtower:v$GENERATOR_VERSION:occupied:$x:$y") % 100L <
        OCCUPANCY_THRESHOLD_PERCENT.toLong()

    private fun buildId(
        x: Int,
        y: Int,
    ): String = "$ID_PREFIX:v$GENERATOR_VERSION:$GENERATOR_TILE_ZOOM:$x:$y"

    private fun buildName(
        x: Int,
        y: Int,
    ): String {
        val adjective = adjectiveParts[stableIndex("watchtower:v$GENERATOR_VERSION:adj:$x:$y", adjectiveParts.size)]
        val noun = nounParts[stableIndex("watchtower:v$GENERATOR_VERSION:noun:$x:$y", nounParts.size)]
        return "$adjective $noun"
    }

    private fun interpolatePadded(
        min: Double,
        max: Double,
        fraction: Double,
    ): Double {
        val paddedFraction = CELL_PADDING_FRACTION + ((1.0 - (CELL_PADDING_FRACTION * 2.0)) * fraction)
        return min + ((max - min) * paddedFraction)
    }

    private fun stableFraction(seed: String): Double =
        stablePositiveHash(seed).toDouble() / HASH_FRACTION_GRANULARITY

    private fun stableIndex(
        seed: String,
        size: Int,
    ): Int = (stablePositiveHash(seed) % size.toLong()).toInt()

    private fun stablePositiveHash(seed: String): Long =
        (seed.hashCode().toLong() and 0x7fffffffL) % HASH_FRACTION_GRANULARITY.toLong()

    private fun parseId(id: String): WatchtowerIdDescriptor? {
        val parts = id.split(':')
        if (parts.size != 5 || parts[0] != ID_PREFIX) {
            return null
        }

        val version = parts[1].removePrefix("v").toIntOrNull() ?: return null
        if (version != GENERATOR_VERSION) {
            return null
        }

        val zoom = parts[2].toIntOrNull() ?: return null
        if (zoom != GENERATOR_TILE_ZOOM) {
            return null
        }

        val x = parts[3].toIntOrNull() ?: return null
        val y = parts[4].toIntOrNull() ?: return null
        if (x !in 0..maxGeneratorTileIndex() || y !in 0..maxGeneratorTileIndex()) {
            return null
        }

        return WatchtowerIdDescriptor(
            x = x,
            y = y,
        )
    }

    private fun generatorRangeForTile(tile: MapTile): ExplorationTileRange? {
        if (!hasValidCoordinatesForZoom(tile)) {
            return null
        }

        return when {
            tile.zoom == GENERATOR_TILE_ZOOM -> singleCellRange(tile.x, tile.y)
            tile.zoom > GENERATOR_TILE_ZOOM -> {
                val shift = tile.zoom - GENERATOR_TILE_ZOOM
                singleCellRange(
                    x = parentAxisCoordinate(tile.x, shift),
                    y = parentAxisCoordinate(tile.y, shift),
                )
            }

            else -> {
                val shift = GENERATOR_TILE_ZOOM - tile.zoom
                val minX = expandedAxisMin(tile.x, shift)
                val maxX = expandedAxisMax(tile.x, shift)
                val minY = expandedAxisMin(tile.y, shift)
                val maxY = expandedAxisMax(tile.y, shift)

                ExplorationTileRange(
                    zoom = GENERATOR_TILE_ZOOM,
                    minX = minX,
                    maxX = maxX,
                    minY = minY,
                    maxY = maxY,
                )
            }
        }
    }

    private fun singleCellRange(
        x: Int,
        y: Int,
    ) = ExplorationTileRange(
        zoom = GENERATOR_TILE_ZOOM,
        minX = x,
        maxX = x,
        minY = y,
        maxY = y,
    )

    private fun parentAxisCoordinate(
        coordinate: Int,
        shift: Int,
    ): Int = when {
        shift <= 0 -> coordinate
        shift > MAX_PARENT_SHIFT -> 0
        else -> coordinate ushr shift
    }

    private fun expandedAxisMin(
        coordinate: Int,
        shift: Int,
    ): Int = ((coordinate.toLong() shl shift).coerceAtMost(maxGeneratorTileIndex().toLong())).toInt()

    private fun expandedAxisMax(
        coordinate: Int,
        shift: Int,
    ): Int = ((((coordinate.toLong() + 1L) shl shift) - 1L).coerceAtMost(maxGeneratorTileIndex().toLong())).toInt()

    private fun hasValidCoordinatesForZoom(tile: MapTile): Boolean {
        if (tile.x < 0 || tile.y < 0) {
            return false
        }

        if (tile.zoom >= Int.SIZE_BITS - 1) {
            return true
        }

        val maxCoordinate = (1 shl tile.zoom) - 1
        return tile.x <= maxCoordinate && tile.y <= maxCoordinate
    }

    private fun maxGeneratorTileIndex(): Int = (1 shl GENERATOR_TILE_ZOOM) - 1

    private data class WatchtowerIdDescriptor(
        val x: Int,
        val y: Int,
    )
}
