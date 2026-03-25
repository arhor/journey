package com.github.arhor.journey.domain.model

object PackedExplorationTileCoordinates {

    const val MAX_ZOOM: Int = 0xFFFF
    const val MAX_AXIS_COORDINATE: Int = 0xFFFFFF

    fun pack(tile: ExplorationTile): Long = pack(
        zoom = tile.zoom,
        x = tile.x,
        y = tile.y,
    )

    fun pack(
        zoom: Int,
        x: Int,
        y: Int,
    ): Long = (zoom.toLong() shl ZOOM_SHIFT) or
        ((x.toLong() and AXIS_COORDINATE_MASK) shl X_SHIFT) or
        (y.toLong() and AXIS_COORDINATE_MASK)

    fun unpackZoom(value: Long): Int = (value ushr ZOOM_SHIFT).toInt()

    fun unpackX(value: Long): Int = ((value ushr X_SHIFT) and AXIS_COORDINATE_MASK).toInt()

    fun unpackY(value: Long): Int = (value and AXIS_COORDINATE_MASK).toInt()

    private const val ZOOM_SHIFT = 48
    private const val X_SHIFT = 24
    private const val AXIS_COORDINATE_MASK = 0xFFFFFFL
}
