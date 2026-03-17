package com.github.arhor.journey.domain.model

object ExplorationTilePrototype {
    /**
     * Fixed canonical zoom for the tile/quadtree prototype.
     *
     * Exploration storage stays at this zoom regardless of the visual map zoom.
     */
    const val CANONICAL_ZOOM = 20

    /**
     * Prototype reveal radius around the live user location.
     *
     * The current implementation expands this into a simple north-aligned geographic bounds before
     * enumerating intersecting canonical tiles.
     */
    const val REVEAL_RADIUS_METERS = 20.0
}
