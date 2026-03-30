package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.ExplorationTileRange
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

    fun calculateExploredTileRanges(
        tileRange: ExplorationTileRange?,
        exploredTiles: Set<MapTile>,
    ): List<ExplorationTileRange> = calculateRanges(
        tileRange = tileRange,
        tileKeys = exploredTiles.toTileKeysFor(tileRange),
        includeMatchingTiles = true,
    )

    private fun calculateRanges(
        tileRange: ExplorationTileRange?,
        tileKeys: Set<Long>,
        includeMatchingTiles: Boolean,
    ): List<ExplorationTileRange> {
        if (tileRange == null) {
            return emptyList()
        }
        val completedRanges = mutableListOf<ExplorationTileRange>()
        var activeRanges = mutableMapOf<RowSpan, MutableFogRange>()

        for (y in tileRange.minY..tileRange.maxY) {
            val currentRanges = mutableMapOf<RowSpan, MutableFogRange>()

            for (span in rowSpans(tileRange, tileKeys, y, includeMatchingTiles)) {
                val continuedRange = activeRanges.remove(span)

                if (continuedRange != null) {
                    continuedRange.maxY = y
                    currentRanges[span] = continuedRange
                } else {
                    currentRanges[span] = MutableFogRange(
                        minX = span.minX,
                        maxX = span.maxX,
                        minY = y,
                        maxY = y,
                    )
                }
            }

            completedRanges += activeRanges.values.map { it.toRange(tileRange.zoom) }
            activeRanges = currentRanges
        }

        completedRanges += activeRanges.values.map { it.toRange(tileRange.zoom) }

        return completedRanges
    }

    private fun rowSpans(
        visibleRange: ExplorationTileRange,
        tileKeys: Set<Long>,
        y: Int,
        includeMatchingTiles: Boolean,
    ): List<RowSpan> {
        val spans = mutableListOf<RowSpan>()
        var spanStartX: Int? = null

        for (x in visibleRange.minX..visibleRange.maxX) {
            val hasMatchingTile = MapTile.pack(
                zoom = visibleRange.zoom,
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
                spans += RowSpan(
                    minX = spanStartX,
                    maxX = x - 1,
                )
                spanStartX = null
            }
        }

        if (spanStartX != null) {
            spans += RowSpan(
                minX = spanStartX,
                maxX = visibleRange.maxX,
            )
        }

        return spans
    }

    private data class RowSpan(
        val minX: Int,
        val maxX: Int,
    )

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

    private fun Set<MapTile>.toTileKeysFor(tileRange: ExplorationTileRange?): Set<Long> {
        if (tileRange == null) {
            return emptySet()
        }

        return asSequence()
            .filter { tileRange.contains(it) }
            .mapTo(HashSet(), MapTile::packedValue)
    }
}
