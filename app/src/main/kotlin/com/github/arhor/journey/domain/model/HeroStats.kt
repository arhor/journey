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
) {
    operator fun plus(delta: StatsDelta): HeroStats =
        HeroStats(
            strength = (strength + delta.strength).coerceAtLeast(0),
            vitality = (vitality + delta.vitality).coerceAtLeast(0),
            dexterity = (dexterity + delta.dexterity).coerceAtLeast(0),
            stamina = (stamina + delta.stamina).coerceAtLeast(0),
        )
}
