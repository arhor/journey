package com.github.arhor.journey.domain.exploration.model

import java.time.Instant

data class DiscoveredPoi(
    val poiId: String,
    val discoveredAt: Instant,
)
