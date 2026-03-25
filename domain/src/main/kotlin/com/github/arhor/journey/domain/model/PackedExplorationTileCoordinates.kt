package com.github.arhor.journey.domain.model

/**
 * Canonical packed tile layout:
 * - bits 63..56: reserved
 * - bits 55..48: zoom (8 bits)
 * - bits 47..24: x (24 bits)
 * - bits 23..0: y (24 bits)
 */
object PackedExplorationTileCoordinates {

    const val MAX_ZOOM: Int = 0xFF
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
    ): Long =
        ((zoom.toLong() and ZOOM_MASK) shl ZOOM_SHIFT) or
            ((x.toLong() and AXIS_COORDINATE_MASK) shl X_SHIFT) or
            ((y.toLong() and AXIS_COORDINATE_MASK) shl Y_SHIFT)

    fun unpackZoom(value: Long): Int = ((value ushr ZOOM_SHIFT) and ZOOM_MASK).toInt()

    fun unpackX(value: Long): Int = ((value ushr X_SHIFT) and AXIS_COORDINATE_MASK).toInt()

    fun unpackY(value: Long): Int = ((value ushr Y_SHIFT) and AXIS_COORDINATE_MASK).toInt()

    private const val ZOOM_SHIFT = 48
    private const val X_SHIFT = 24
    private const val Y_SHIFT = 0

    private const val ZOOM_MASK = 0xFFL
    private const val AXIS_COORDINATE_MASK = 0xFFFFFFL
}
