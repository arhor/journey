package com.github.arhor.journey.domain.model

import java.time.Instant

/**
 * The player-controlled hero.
 *
 * The current foundation assumes a single "current hero" persisted locally, but keeps [id] explicit to allow
 * future multi-hero support.
 */
data class Hero(
    val id: String,
    val name: String,
    val stats: HeroStats,
    val progression: Progression,
    val createdAt: Instant,
    val updatedAt: Instant,
)

