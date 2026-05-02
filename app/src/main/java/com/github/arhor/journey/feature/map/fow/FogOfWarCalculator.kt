package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.MapTile
import javax.inject.Inject

class FogOfWarCalculator @Inject constructor() {

    fun calculateUnexploredFogRanges(
        tileRange: ExplorationTileRange?,
        exploredTiles: Set<MapTile>,
    ): List<ExplorationTileRange> = calculateRanges(
        tileRange = tileRange,
        tileKeys = exploredTiles.toTileKeysFor(tileRange),
        includeMatchingTiles = false,
    )

    fun calculateUnexploredFogRanges(
        tileRange: ExplorationTileRange?,
        exploredTileKeys: LongArray,
    ): List<ExplorationTileRange> = calculateRanges(
        tileRange = tileRange,
        tileKeys = PackedTileSet.fromPacked(exploredTileKeys),
        includeMatchingTiles = false,
    )

    internal fun calculateUnexploredFogRanges(
        tileRange: ExplorationTileRange?,
        exploredTileKeys: PackedTileSet,
    ): List<ExplorationTileRange> = calculateRanges(
        tileRange = tileRange,
        tileKeys = exploredTileKeys,
        includeMatchingTiles = false,
    )

    fun calculateExploredTileRanges(
        tileRange: ExplorationTileRange?,
        exploredTiles: Set<MapTile>,
    ): List<ExplorationTileRange> = calculateRanges(
        tileRange = tileRange,
        tileKeys = exploredTiles.toTileKeysFor(tileRange),
        includeMatchingTiles = true,
    )

    fun calculateExploredTileRanges(
        tileRange: ExplorationTileRange?,
        exploredTileKeys: LongArray,
    ): List<ExplorationTileRange> = calculateRanges(
        tileRange = tileRange,
        tileKeys = PackedTileSet.fromPacked(exploredTileKeys),
        includeMatchingTiles = true,
    )

    internal fun calculateExploredTileRanges(
        tileRange: ExplorationTileRange?,
        exploredTileKeys: PackedTileSet,
    ): List<ExplorationTileRange> = calculateRanges(
        tileRange = tileRange,
        tileKeys = exploredTileKeys,
        includeMatchingTiles = true,
    )

    private fun calculateRanges(
        tileRange: ExplorationTileRange?,
        tileKeys: PackedTileSet,
        includeMatchingTiles: Boolean,
    ): List<ExplorationTileRange> {
        if (tileRange == null) {
            return emptyList()
        }
        validateRangeCanBePacked(tileRange)

        val completedRanges = mutableListOf<ExplorationTileRange>()
        var activeRanges = mutableMapOf<Long, MutableFogRange>()

        for (y in tileRange.minY..tileRange.maxY) {
            val currentRanges = mutableMapOf<Long, MutableFogRange>()
            var spanStartX: Int? = null

            for (x in tileRange.minX..tileRange.maxX) {
                val hasMatchingTile = packedTileKey(
                    zoom = tileRange.zoom,
                    x = x,
                    y = y,
                ) in tileKeys
                val shouldIncludeTile = if (includeMatchingTiles) {
                    hasMatchingTile
                } else {
                    !hasMatchingTile
                }

                if (shouldIncludeTile && spanStartX == null) {
                    spanStartX = x
                } else if (!shouldIncludeTile && spanStartX != null) {
                    upsertRowSpan(
                        activeRanges = activeRanges,
                        currentRanges = currentRanges,
                        minX = spanStartX,
                        maxX = x - 1,
                        y = y,
                    )
                    spanStartX = null
                }
            }

            if (spanStartX != null) {
                upsertRowSpan(
                    activeRanges = activeRanges,
                    currentRanges = currentRanges,
                    minX = spanStartX,
                    maxX = tileRange.maxX,
                    y = y,
                )
            }

            activeRanges.values.forEach { completedRanges += it.toRange(tileRange.zoom) }
            activeRanges = currentRanges
        }

        activeRanges.values.forEach { completedRanges += it.toRange(tileRange.zoom) }

        return completedRanges
    }

    private fun upsertRowSpan(
        activeRanges: MutableMap<Long, MutableFogRange>,
        currentRanges: MutableMap<Long, MutableFogRange>,
        minX: Int,
        maxX: Int,
        y: Int,
    ) {
        val spanKey = spanKey(minX = minX, maxX = maxX)
        val continuedRange = activeRanges.remove(spanKey)

        if (continuedRange != null) {
            continuedRange.maxY = y
            currentRanges[spanKey] = continuedRange
        } else {
            currentRanges[spanKey] = MutableFogRange(
                minX = minX,
                maxX = maxX,
                minY = y,
                maxY = y,
            )
        }
    }

    private data class MutableFogRange(
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        var maxY: Int,
    ) {
        fun toRange(zoom: Int): ExplorationTileRange = ExplorationTileRange(
            zoom = zoom,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
        )
    }

    private fun Set<MapTile>.toTileKeysFor(tileRange: ExplorationTileRange?): PackedTileSet {
        if (tileRange == null || isEmpty()) {
            return PackedTileSet.Empty
        }

        val keys = LongArray(size)
        var keyCount = 0
        for (tile in this) {
            if (tileRange.contains(tile)) {
                keys[keyCount++] = tile.packedValue
            }
        }

        return when (keyCount) {
            0 -> PackedTileSet.Empty
            size -> PackedTileSet.fromPacked(keys)
            else -> PackedTileSet.fromPacked(keys.copyOf(keyCount))
        }
    }

    private fun spanKey(
        minX: Int,
        maxX: Int,
    ): Long = (minX.toLong() shl Int.SIZE_BITS) or (maxX.toLong() and UINT_MASK)

    private fun validateRangeCanBePacked(tileRange: ExplorationTileRange) {
        MapTile.pack(
            zoom = tileRange.zoom,
            x = tileRange.minX,
            y = tileRange.minY,
        )
        MapTile.pack(
            zoom = tileRange.zoom,
            x = tileRange.maxX,
            y = tileRange.maxY,
        )
    }

    private companion object {
        const val UINT_MASK = 0xFFFF_FFFFL
    }
}
