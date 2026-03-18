package com.github.arhor.journey.domain.model

object ExplorationTilePrototype {
    /**
     * Fixed canonical zoom for the tile/quadtree prototype.
     *
     * Exploration storage stays at this zoom regardless of the visual map zoom.
     */
    const val CANONICAL_ZOOM = 19

    /**
     * Current player light profile expressed as Chebyshev-distance tile rings.
     *
     * Future systems can layer additional light sources on top of this baseline profile.
     */
    const val PLAYER_LIGHT_MAX_RING = 2
    const val CURRENT_TILE_LIGHT = 1.0f
    const val FIRST_RING_LIGHT = 0.66f
    const val SECOND_RING_LIGHT = 0.33f
}
