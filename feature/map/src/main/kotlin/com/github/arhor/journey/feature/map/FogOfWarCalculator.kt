package com.github.arhor.journey.feature.map

import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileLight
import com.github.arhor.journey.domain.model.ExplorationTileRange

internal fun calculateFogOfWarBands(
    tileRange: ExplorationTileRange?,
    tileLightByTile: Map<ExplorationTile, Float>,
): List<FogOfWarBandUiState> {
    tileRange ?: return emptyList()

    val completedRanges = mutableListOf<CompletedFogRange>()
    var activeRanges = mutableMapOf<RowSpan, MutableFogRange>()

    for (y in tileRange.minY..tileRange.maxY) {
        val currentRanges = mutableMapOf<RowSpan, MutableFogRange>()

        for (span in rowSpans(tileRange, tileLightByTile, y)) {
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
                    opacityBits = span.opacityBits,
                )
            }
        }

        completedRanges += activeRanges.values.map { it.toCompletedRange(tileRange.zoom) }
        activeRanges = currentRanges
    }

    completedRanges += activeRanges.values.map { it.toCompletedRange(tileRange.zoom) }

    return completedRanges
        .groupBy(CompletedFogRange::opacityBits)
        .toSortedMap(compareByDescending<Int> { Float.fromBits(it) })
        .map { (opacityBits, fogRanges) ->
            FogOfWarBandUiState(
                opacity = Float.fromBits(opacityBits),
                ranges = fogRanges.map(CompletedFogRange::range),
            )
        }
}

private fun rowSpans(
    visibleRange: ExplorationTileRange,
    tileLightByTile: Map<ExplorationTile, Float>,
    y: Int,
): List<RowSpan> {
    val spans = mutableListOf<RowSpan>()
    var spanStartX: Int? = null
    var spanOpacityBits: Int? = null

    for (x in visibleRange.minX..visibleRange.maxX) {
        val opacity = fogOpacity(
            light = tileLightByTile[
                ExplorationTile(
                    zoom = visibleRange.zoom,
                    x = x,
                    y = y,
                ),
            ] ?: ExplorationTileLight.MIN_LIGHT,
        )
        val opacityBits = opacity.toBits()

        if (opacityBits == ZERO_OPACITY_BITS) {
            if (spanStartX != null && spanOpacityBits != null) {
                spans += RowSpan(
                    minX = spanStartX,
                    maxX = x - 1,
                    opacityBits = checkNotNull(spanOpacityBits),
                )
                spanStartX = null
                spanOpacityBits = null
            }
            continue
        }

        if (spanStartX == null) {
            spanStartX = x
            spanOpacityBits = opacityBits
            continue
        }

        if (spanOpacityBits != opacityBits) {
            spans += RowSpan(
                minX = spanStartX,
                maxX = x - 1,
                opacityBits = checkNotNull(spanOpacityBits),
            )
            spanStartX = x
            spanOpacityBits = opacityBits
        }
    }

    if (spanStartX != null && spanOpacityBits != null) {
        spans += RowSpan(
            minX = spanStartX,
            maxX = visibleRange.maxX,
            opacityBits = checkNotNull(spanOpacityBits),
        )
    }

    return spans
}

private fun fogOpacity(light: Float): Float = MAX_FOG_OPACITY * (1.0f - light.coerceIn(0.0f, 1.0f))

private data class RowSpan(
    val minX: Int,
    val maxX: Int,
    val opacityBits: Int,
)

private data class MutableFogRange(
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    var maxY: Int,
    val opacityBits: Int,
) {
    fun toCompletedRange(zoom: Int): CompletedFogRange = CompletedFogRange(
        opacityBits = opacityBits,
        range = ExplorationTileRange(
            zoom = zoom,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
        ),
    )
}

private data class CompletedFogRange(
    val opacityBits: Int,
    val range: ExplorationTileRange,
)

private const val MAX_FOG_OPACITY = 0.90f
private val ZERO_OPACITY_BITS = 0.0f.toBits()
