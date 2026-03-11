package com.github.arhor.journey.domain.model

import com.github.arhor.journey.domain.player.model.StatsDelta

/**
 * A deterministic outcome of some user action (for example logging an activity).
 *
 * This is a domain value. Persistence may store only a subset of fields initially.
 */
data class Reward(
    val xp: Long,
    val energyDelta: Int = 0,
    val stats: StatsDelta = StatsDelta(),
)
