package com.github.arhor.journey.domain.model

/**
 * Packed representation of a map tile coordinate.
 *
 * The tile is stored inside a single [Long] using the following bit layout:
 *
 * - bits 48..55: `zoom`   (8 bits)
 * - bits 24..47: `y`      (24 bits)
 * - bits  0..23: `x`      (24 bits)
 *
 * In other words, the memory layout is:
 *
 * `[zoom | y | x]`
 *
 * This representation is useful when compact storage, fast comparisons,
 * or low-allocation transfer of tile coordinates are important.
 *
 * Natural ordering by [value] follows the packed layout, meaning values are
 * effectively ordered by `zoom`, then `y`, then `x`.
 *
 * All coordinates are expected to be non-negative and to fit into their
 * allocated bit ranges:
 *
 * - `zoom`: 0..255
 * - `x`: 0..16_777_215
 * - `y`: 0..16_777_215
 *
 * The constructor is private to guarantee that all instances are created
 * through validated packing.
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
        private const val Y_SHIFT = 24
        private const val X_SHIFT = 0

        private const val ZOOM_COORDINATE_MASK = 0xFFL
        private const val AXIS_COORDINATE_MASK = 0xFFFFFFL

        /**
         * Creates a [MapTile] from zoom, x, and y coordinates.
         *
         * @param zoom tile zoom level, must be in range `0..255`
         * @param x tile X coordinate, must be in range `0..16_777_215`
         * @param y tile Y coordinate, must be in range `0..16_777_215`
         *
         * @throws IllegalArgumentException if any value is negative
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
         * Packs tile coordinates into a single [Long] using layout `[zoom|y|x]`.
         *
         * Bits allocation:
         *
         * - `zoom`: 8 bits
         * - `y`: 24 bits
         * - `x`: 24 bits
         *
         * @param zoom tile zoom level, must be in range `0..255`
         * @param x tile X coordinate, must be in range `0..16_777_215`
         * @param y tile Y coordinate, must be in range `0..16_777_215`
         *
         * @return packed tile value
         *
         * @throws IllegalArgumentException if any value is negative
         */
        fun pack(zoom: Int, x: Int, y: Int): Long {
            require(zoom >= 0) { "zoom must be >= 0" }
            require(x >= 0) { "x must be >= 0" }
            require(y >= 0) { "y must be >= 0" }

            return ((zoom.toLong() and ZOOM_COORDINATE_MASK) shl ZOOM_SHIFT) or
                ((y.toLong() and AXIS_COORDINATE_MASK) shl Y_SHIFT) or
                ((x.toLong() and AXIS_COORDINATE_MASK) shl X_SHIFT)
        }

        fun unpackZoom(value: Long): Int = ((value ushr ZOOM_SHIFT) and ZOOM_COORDINATE_MASK).toInt()

        fun unpackX(value: Long): Int = ((value ushr X_SHIFT) and AXIS_COORDINATE_MASK).toInt()

        fun unpackY(value: Long): Int = ((value ushr Y_SHIFT) and AXIS_COORDINATE_MASK).toInt()
    }
}
