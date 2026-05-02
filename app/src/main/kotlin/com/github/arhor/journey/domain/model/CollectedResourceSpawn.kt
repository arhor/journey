package com.github.arhor.journey.domain.model

import java.time.Instant

data class CollectedResourceSpawn(
    val heroId: String,
    val typeId: String,
    val spawnId: String,
    val collectedAt: Instant,
)
