package com.github.arhor.journey.feature.map.fow

import androidx.collection.LongSet
import androidx.collection.MutableLongSet
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.MapTile

internal class PackedTileSet private constructor(
    private val sortedKeys: LongArray,
    private val lookup: LongSet,
) {
    val size: Int get() = sortedKeys.size

    fun isEmpty(): Boolean = sortedKeys.isEmpty()

    fun keyAt(index: Int): Long = sortedKeys[index]

    operator fun contains(key: Long): Boolean = lookup.contains(key)

    fun union(other: PackedTileSet): PackedTileSet = when {
        isEmpty() -> other
        other.isEmpty() -> this
        this == other -> this
        else -> fromSortedUnique(mergeSortedUnique(sortedKeys, other.sortedKeys))
    }

    fun minus(other: PackedTileSet): PackedTileSet {
        if (isEmpty() || other.isEmpty()) {
            return this
        }

        val result = LongArray(size)
        var resultSize = 0

        for (index in 0 until size) {
            val key = sortedKeys[index]
            if (key !in other) {
                result[resultSize++] = key
            }
        }

        return when (resultSize) {
            0 -> Empty
            size -> this
            else -> fromSortedUnique(result.copyOf(resultSize))
        }
    }

    override fun equals(other: Any?): Boolean =
        other is PackedTileSet && sortedKeys.contentEquals(other.sortedKeys)

    override fun hashCode(): Int = sortedKeys.contentHashCode()

    companion object {
        val Empty = PackedTileSet(
            sortedKeys = LongArray(0),
            lookup = androidx.collection.emptyLongSet(),
        )

        fun fromPacked(keys: LongArray): PackedTileSet {
            if (keys.isEmpty()) {
                return Empty
            }

            val sorted = keys.copyOf()
            sorted.sort()

            val uniqueSize = deduplicateSortedInPlace(sorted)
            val unique = if (uniqueSize == sorted.size) {
                sorted
            } else {
                sorted.copyOf(uniqueSize)
            }

            return fromSortedUnique(unique)
        }

        fun fromTiles(tiles: Set<MapTile>): PackedTileSet {
            if (tiles.isEmpty()) {
                return Empty
            }

            val keys = LongArray(tiles.size)
            var index = 0
            for (tile in tiles) {
                keys[index++] = tile.packedValue
            }

            return fromPacked(keys)
        }

        private fun fromSortedUnique(keys: LongArray): PackedTileSet {
            if (keys.isEmpty()) {
                return Empty
            }

            val lookup = MutableLongSet(keys.size)
            for (key in keys) {
                lookup.add(key)
            }

            return PackedTileSet(
                sortedKeys = keys,
                lookup = lookup,
            )
        }
    }
}

internal data class PackedTileMask(
    val tiles: Set<MapTile>,
    val packedTiles: PackedTileSet,
) {
    val isEmpty: Boolean get() = packedTiles.isEmpty()

    companion object {
        val Empty = PackedTileMask(
            tiles = emptySet(),
            packedTiles = PackedTileSet.Empty,
        )

        fun fromTiles(tiles: Set<MapTile>): PackedTileMask =
            if (tiles.isEmpty()) {
                Empty
            } else {
                PackedTileMask(
                    tiles = tiles,
                    packedTiles = PackedTileSet.fromTiles(tiles),
                )
            }

        fun merge(
            first: PackedTileMask,
            second: PackedTileMask,
        ): PackedTileMask = when {
            first.isEmpty -> second
            second.isEmpty -> first
            first == second -> first
            else -> PackedTileMask(
                tiles = buildSet(first.tiles.size + second.tiles.size) {
                    addAll(first.tiles)
                    addAll(second.tiles)
                },
                packedTiles = first.packedTiles.union(second.packedTiles),
            )
        }
    }
}

internal fun packedTileKey(
    zoom: Int,
    x: Int,
    y: Int,
): Long =
    ((zoom.toLong() and MapTile.ZOOM_COORDINATE_MASK) shl MapTile.ZOOM_SHIFT) or
        ((x.toLong() and MapTile.AXIS_COORDINATE_MASK) shl MapTile.X_SHIFT) or
        ((y.toLong() and MapTile.AXIS_COORDINATE_MASK) shl MapTile.Y_SHIFT)

internal fun ExplorationTileRange.containsPackedTile(key: Long): Boolean =
    MapTile.unpackZoom(key) == zoom &&
        MapTile.unpackX(key) in minX..maxX &&
        MapTile.unpackY(key) in minY..maxY

private fun deduplicateSortedInPlace(keys: LongArray): Int {
    var uniqueSize = 1

    for (readIndex in 1 until keys.size) {
        val key = keys[readIndex]
        if (key != keys[uniqueSize - 1]) {
            keys[uniqueSize++] = key
        }
    }

    return uniqueSize
}

private fun mergeSortedUnique(
    first: LongArray,
    second: LongArray,
): LongArray {
    val result = LongArray(first.size + second.size)
    var firstIndex = 0
    var secondIndex = 0
    var resultSize = 0

    while (firstIndex < first.size && secondIndex < second.size) {
        val firstKey = first[firstIndex]
        val secondKey = second[secondIndex]

        when {
            firstKey < secondKey -> {
                result[resultSize++] = firstKey
                firstIndex++
            }

            firstKey > secondKey -> {
                result[resultSize++] = secondKey
                secondIndex++
            }

            else -> {
                result[resultSize++] = firstKey
                firstIndex++
                secondIndex++
            }
        }
    }

    while (firstIndex < first.size) {
        result[resultSize++] = first[firstIndex++]
    }
    while (secondIndex < second.size) {
        result[resultSize++] = second[secondIndex++]
    }

    return result.copyOf(resultSize)
}
