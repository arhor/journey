package com.github.arhor.journey.feature.map

import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileRange

internal fun calculateUnexploredFogRanges(
    visibleRange: ExplorationTileRange?,
    exploredTiles: Set<ExplorationTile>,
): List<ExplorationTileRange> {
    visibleRange ?: return emptyList()

    val completedRanges = mutableListOf<ExplorationTileRange>()
    var activeRanges = mutableMapOf<RowSpan, MutableFogRange>()

    for (y in visibleRange.minY..visibleRange.maxY) {
        val currentRanges = mutableMapOf<RowSpan, MutableFogRange>()

        for (span in rowSpans(visibleRange, exploredTiles, y)) {
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

        completedRanges += activeRanges.values.map { it.toRange(visibleRange.zoom) }
        activeRanges = currentRanges
    }

    completedRanges += activeRanges.values.map { it.toRange(visibleRange.zoom) }

    return completedRanges
}

private fun rowSpans(
    visibleRange: ExplorationTileRange,
    exploredTiles: Set<ExplorationTile>,
    y: Int,
): List<RowSpan> {
    val spans = mutableListOf<RowSpan>()
    var spanStartX: Int? = null

    for (x in visibleRange.minX..visibleRange.maxX) {
        val isExplored = exploredTiles.contains(
            ExplorationTile(
                zoom = visibleRange.zoom,
                x = x,
                y = y,
            ),
        )

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
