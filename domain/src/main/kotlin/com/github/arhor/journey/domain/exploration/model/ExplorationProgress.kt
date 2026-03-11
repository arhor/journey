package com.github.arhor.journey.domain.exploration.model

import com.github.arhor.journey.domain.exploration.model.DiscoveredPoi

/**
 * Lightweight exploration state.
 *
 * The foundation models discovery as a set of discovered POIs. Area/tiles/fog-of-war can be layered later.
 */
data class ExplorationProgress(
    val discovered: Set<DiscoveredPoi>,
)
