package com.github.arhor.journey.domain.model

/**
 * Packed representation of a map tile coordinate.
 *
 * The tile is stored inside a single [Long] using the following bit layout:
 *
 * - bits 48..55: `zoom` (8 bits)
 * - bits 24..47: `x`    (24 bits)
 * - bits  0..23: `y`    (24 bits)
 *
 * In other words, the memory layout is:
 *
 * `[zoom | x | y]`
 *
 * Natural ordering by [value] follows the packed layout, meaning values are
 * effectively ordered by `zoom`, then `x`, then `y`.
 */
@JvmInline
value class MapTile private constructor(
    val value: Long,
) {
    /** Zoom level extracted from the packed [value]. */
    val zoom: Int get() = unpackZoom(value)

    /** X coordinate extracted from the packed [value]. */
    val x: Int get() = unpackX(value)

    /** Y coordinate extracted from the packed [value]. */
    val y: Int get() = unpackY(value)

    companion object {
        private const val ZOOM_SHIFT = 48
        private const val X_SHIFT = 24
        private const val Y_SHIFT = 0

        private const val ZOOM_COORDINATE_MASK = 0xFFL
        private const val AXIS_COORDINATE_MASK = 0xFFFFFFL

        /**
         * Creates a [MapTile] from zoom, x, and y coordinates.
         */
        operator fun invoke(zoom: Int, x: Int, y: Int): MapTile {
            return MapTile(
                value = pack(
                    zoom = zoom,
                    x = x,
                    y = y,
                ),
            )
        }

        /**
         * Packs tile coordinates into a single [Long] using layout `[zoom|x|y]`.
         *
         * @param zoom tile zoom level, must be in range `0..255`
         * @param x tile X coordinate, must be in range `0..16_777_215`
         * @param y tile Y coordinate, must be in range `0..16_777_215`
         */
        fun pack(zoom: Int, x: Int, y: Int): Long {
            require(zoom in 0..255) { "zoom must be in 0..255" }
            require(x in 0..16_777_215) { "x must be in 0..16_777_215" }
            require(y in 0..16_777_215) { "y must be in 0..16_777_215" }

            return ((zoom.toLong() and ZOOM_COORDINATE_MASK) shl ZOOM_SHIFT) or
                   ((x.toLong() and AXIS_COORDINATE_MASK) shl X_SHIFT) or
                   ((y.toLong() and AXIS_COORDINATE_MASK) shl Y_SHIFT)
        }

        fun unpackZoom(value: Long): Int =
            ((value ushr ZOOM_SHIFT) and ZOOM_COORDINATE_MASK).toInt()

        fun unpackX(value: Long): Int =
            ((value ushr X_SHIFT) and AXIS_COORDINATE_MASK).toInt()

        fun unpackY(value: Long): Int =
            ((value ushr Y_SHIFT) and AXIS_COORDINATE_MASK).toInt()
    }
}
