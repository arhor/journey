package com.github.arhor.journey.domain.model

data class ExplorationTileRange(
    val zoom: Int,
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int,
) {
    init {
        require(zoom >= 0) { "zoom must be >= 0" }
        require(minX <= maxX) { "minX must be <= maxX" }
        require(minY <= maxY) { "minY must be <= maxY" }
    }

    val tileCount: Long
        get() = (maxX - minX + 1L) * (maxY - minY + 1L)

    fun contains(tile: ExplorationTile): Boolean {
        return tile.zoom == zoom &&
            tile.x in minX..maxX &&
            tile.y in minY..maxY
    }

    fun intersectionOrNull(other: ExplorationTileRange): ExplorationTileRange? {
        if (zoom != other.zoom) {
            return null
        }

        val intersectionMinX = maxOf(minX, other.minX)
        val intersectionMaxX = minOf(maxX, other.maxX)
        val intersectionMinY = maxOf(minY, other.minY)
        val intersectionMaxY = minOf(maxY, other.maxY)

        if (intersectionMinX > intersectionMaxX || intersectionMinY > intersectionMaxY) {
            return null
        }

        return ExplorationTileRange(
            zoom = zoom,
            minX = intersectionMinX,
            maxX = intersectionMaxX,
            minY = intersectionMinY,
            maxY = intersectionMaxY,
        )
    }

    fun subtract(other: ExplorationTileRange): List<ExplorationTileRange> {
        val intersection = intersectionOrNull(other) ?: return listOf(this)
        val remainder = mutableListOf<ExplorationTileRange>()

        if (minY < intersection.minY) {
            remainder += ExplorationTileRange(
                zoom = zoom,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = intersection.minY - 1,
            )
        }

        if (intersection.maxY < maxY) {
            remainder += ExplorationTileRange(
                zoom = zoom,
                minX = minX,
                maxX = maxX,
                minY = intersection.maxY + 1,
                maxY = maxY,
            )
        }

        if (minX < intersection.minX) {
            remainder += ExplorationTileRange(
                zoom = zoom,
                minX = minX,
                maxX = intersection.minX - 1,
                minY = intersection.minY,
                maxY = intersection.maxY,
            )
        }

        if (intersection.maxX < maxX) {
            remainder += ExplorationTileRange(
                zoom = zoom,
                minX = intersection.maxX + 1,
                maxX = maxX,
                minY = intersection.minY,
                maxY = intersection.maxY,
            )
        }

        return remainder
    }

    fun asSequence(): Sequence<ExplorationTile> = sequence {
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                yield(
                    ExplorationTile(
                        zoom = zoom,
                        x = x,
                        y = y,
                    ),
                )
            }
        }
    }

    fun expandedBy(tilePadding: Int): ExplorationTileRange =
        expandedBy(
            horizontalTilePadding = tilePadding,
            verticalTilePadding = tilePadding,
        )

    fun expandedBy(
        horizontalTilePadding: Int,
        verticalTilePadding: Int,
    ): ExplorationTileRange {
        require(horizontalTilePadding >= 0) { "horizontalTilePadding must be >= 0" }
        require(verticalTilePadding >= 0) { "verticalTilePadding must be >= 0" }

        if (horizontalTilePadding == 0 && verticalTilePadding == 0) {
            return this
        }

        val maxTileIndex = (1 shl zoom) - 1

        return copy(
            minX = (minX - horizontalTilePadding).coerceAtLeast(0),
            maxX = (maxX + horizontalTilePadding).coerceAtMost(maxTileIndex),
            minY = (minY - verticalTilePadding).coerceAtLeast(0),
            maxY = (maxY + verticalTilePadding).coerceAtMost(maxTileIndex),
        )
    }
}
