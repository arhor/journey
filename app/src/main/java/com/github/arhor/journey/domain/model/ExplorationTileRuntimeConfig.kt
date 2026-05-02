package com.github.arhor.journey.domain.model

import com.github.arhor.journey.domain.CANONICAL_ZOOM
import com.github.arhor.journey.domain.REVEAL_RADIUS_METERS

data class ExplorationTileRuntimeConfig(
    val canonicalZoom: Int = CANONICAL_ZOOM,
    val revealRadiusMeters: Double = REVEAL_RADIUS_METERS,
) {
    companion object {
        const val MIN_CANONICAL_ZOOM = 0
        const val MAX_CANONICAL_ZOOM = 30
        const val MIN_REVEAL_RADIUS_METERS = 1.0
    }
}
