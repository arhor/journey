package com.github.arhor.journey.domain.model

import java.time.Instant

data class HeroResource(
    val heroId: String,
    val resourceTypeId: String,
    val amount: Int,
    val updatedAt: Instant,
)
