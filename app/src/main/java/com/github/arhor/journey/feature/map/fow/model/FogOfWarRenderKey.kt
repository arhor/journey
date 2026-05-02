package com.github.arhor.journey.feature.map.fow.model

import com.github.arhor.journey.domain.model.ExplorationTileRange

internal data class FogOfWarRenderKey(
    val ranges: List<ExplorationTileRange>,
) {
    companion object {
        private val ExplorationTileRangeComparator = compareBy(
            ExplorationTileRange::zoom,
            ExplorationTileRange::minY,
            ExplorationTileRange::minX,
            ExplorationTileRange::maxY,
            ExplorationTileRange::maxX,
        )

        fun of(ranges: List<ExplorationTileRange>): FogOfWarRenderKey = FogOfWarRenderKey(
            ranges = ranges.sortedWith(ExplorationTileRangeComparator),
        )
    }
}
