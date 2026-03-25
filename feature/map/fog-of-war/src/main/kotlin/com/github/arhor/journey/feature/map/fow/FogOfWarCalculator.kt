package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileRange
import javax.inject.Inject

class FogOfWarCalculator @Inject constructor() {

    fun calculateUnexploredFogRanges(
        tileRange: ExplorationTileRange?,
        exploredTiles: Set<ExplorationTile>,
    ): List<ExplorationTileRange> {
        if (tileRange == null) {
            return emptyList()
        }

        val exploredTileKeys = exploredTiles
            .asSequence()
            .filter { it.zoom == tileRange.zoom }
            .map(::packTileCoordinates)
            .toHashSet()

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
            val isExplored = packTileCoordinates(
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

    private fun packTileCoordinates(tile: ExplorationTile): Long = packTileCoordinates(
        zoom = tile.zoom,
        x = tile.x,
        y = tile.y,
    )

    private fun packTileCoordinates(
        zoom: Int,
        x: Int,
        y: Int,
    ): Long = (zoom.toLong() shl 48) or
        ((x.toLong() and AXIS_COORDINATE_MASK) shl 24) or
        (y.toLong() and AXIS_COORDINATE_MASK)

    private companion object {
        const val AXIS_COORDINATE_MASK = 0xFFFFFFL
    }
}
