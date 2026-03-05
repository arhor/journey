package com.github.arhor.journey.domain.model

/**
 * A deterministic outcome of some user action (for example logging an activity).
 *
 * This is a domain value. Persistence may store only a subset of fields initially.
 */
data class Reward(
    val xp: Long,
    val stats: StatsDelta = StatsDelta(),
)

