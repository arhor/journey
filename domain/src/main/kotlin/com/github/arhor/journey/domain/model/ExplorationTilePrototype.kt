package com.github.arhor.journey.domain.model

object ExplorationTilePrototype {
    /**
     * Fixed canonical zoom for the tile/quadtree prototype.
     *
     * Exploration storage stays at this zoom regardless of the visual map zoom.
     */
    const val CANONICAL_ZOOM = 21

    /**
     * Prototype reveal radius around the live user location.
     *
     * The current implementation uses north-aligned geographic bounds as a coarse search area,
     * then keeps only canonical tiles that intersect the circular reveal radius.
     */
    const val REVEAL_RADIUS_METERS = 50.0
}
