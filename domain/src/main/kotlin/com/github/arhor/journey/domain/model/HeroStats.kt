package com.github.arhor.journey.domain.model

/**
 * Base, persistent stats for the hero.
 *
 * This is intentionally small and flat. Derived or computed stats should live outside of this model.
 */
data class HeroStats(
    val strength: Int,
    val vitality: Int,
    val dexterity: Int,
    val stamina: Int,
)
