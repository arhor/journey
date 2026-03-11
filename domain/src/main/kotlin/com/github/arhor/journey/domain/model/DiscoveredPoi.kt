package com.github.arhor.journey.domain.model

import java.time.Instant

data class DiscoveredPoi(
    val poiId: String,
    val discoveredAt: Instant,
)
