package com.github.arhor.journey.domain.model

data class ExplorationTileRuntimeConfig(
    val canonicalZoom: Int = ExplorationTilePrototype.CANONICAL_ZOOM,
    val revealRadiusMeters: Double = ExplorationTilePrototype.REVEAL_RADIUS_METERS,
) {
    companion object {
        const val MIN_CANONICAL_ZOOM = 0
        const val MAX_CANONICAL_ZOOM = 30
        const val MIN_REVEAL_RADIUS_METERS = 1.0
    }
}
