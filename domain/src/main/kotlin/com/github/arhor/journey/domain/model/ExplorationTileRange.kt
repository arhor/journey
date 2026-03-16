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
}
