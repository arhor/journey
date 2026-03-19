package com.github.arhor.journey.domain.model

import java.time.Instant

data class CollectedResourceSpawn(
    val heroId: String,
    val spawnId: String,
    val resourceTypeId: String,
    val collectedAt: Instant,
)
