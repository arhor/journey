package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.PackedExplorationTileCoordinates
import javax.inject.Inject

class FogOfWarCalculator @Inject constructor() {

    fun calculateUnexploredFogRanges(
        tileRange: ExplorationTileRange?,
        packedExploredTiles: LongArray,
    ): List<ExplorationTileRange> {
        if (tileRange == null) {
            return emptyList()
        }

        val exploredTileKeys = packedExploredTiles.toHashSet()

        val completedRanges = mutableListOf<ExplorationTileRange>()
        var activeRanges = mutableMapOf<RowSpan, MutableFogRange>()

        for (y in tileRange.minY..tileRange.maxY) {
            val currentRanges = mutableMapOf<RowSpan, MutableFogRange>()

            for (span in rowSpans(tileRange, exploredTileKeys, y)) {
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
        exploredTileKeys: Set<Long>,
        y: Int,
    ): List<RowSpan> {
        val spans = mutableListOf<RowSpan>()
        var spanStartX: Int? = null

        for (x in visibleRange.minX..visibleRange.maxX) {
            val isExplored = PackedExplorationTileCoordinates.pack(
                zoom = visibleRange.zoom,
                x = x,
                y = y,
            ) in exploredTileKeys

            if (!isExplored && spanStartX == null) {
                spanStartX = x
            } else if (isExplored && spanStartX != null) {
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
}
