package com.github.arhor.journey.feature.map.fow.model

import com.github.arhor.journey.feature.map.fow.approximatelyEquals

internal data class TileRegionRing(
    val points: List<GridPoint>,
) {
    init {
        require(points.size >= 4) { "A tile region ring must contain at least 4 points." }
        require(points.first().approximatelyEquals(points.last())) { "A tile region ring must be closed." }
    }
}
