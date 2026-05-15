package com.github.arhor.journey.domain.model

import java.time.Instant

data class BreachNodeState(
    val breachNodeId: String,
    val h3CellId: String,
    val discoveredAt: Instant?,
    val controlledAt: Instant?,
    val lockdownUntil: Instant?,
    val updatedAt: Instant,
)
