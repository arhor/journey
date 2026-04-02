package com.github.arhor.journey.domain.model

import java.time.Instant

data class WatchtowerState(
    val watchtowerId: String,
    val discoveredAt: Instant,
    val claimedAt: Instant?,
    val level: Int,
    val updatedAt: Instant,
)
