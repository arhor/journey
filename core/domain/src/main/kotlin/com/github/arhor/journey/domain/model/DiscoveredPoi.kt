package com.github.arhor.journey.domain.model

import java.time.Instant

data class DiscoveredPoi(
    val poiId: Long,
    val discoveredAt: Instant,
)
