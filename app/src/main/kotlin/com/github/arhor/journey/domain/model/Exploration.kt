package com.github.arhor.journey.domain.model

import java.time.Instant

data class DiscoveredPoi(
    val poiId: String,
    val discoveredAt: Instant,
)

/**
 * Lightweight exploration state.
 *
 * The foundation models discovery as a set of discovered POIs. Area/tiles/fog-of-war can be layered later.
 */
data class ExplorationProgress(
    val discovered: Set<DiscoveredPoi>,
)

