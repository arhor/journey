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

/**
 * A change applied to [HeroStats].
 *
 * This is used both for explicit rewards and for level-up bonuses. Values can be negative, but
 * the application logic clamps resulting stats to >= 0.
 */
data class StatsDelta(
    val strength: Int = 0,
    val vitality: Int = 0,
    val dexterity: Int = 0,
    val stamina: Int = 0,
)

fun HeroStats.apply(delta: StatsDelta): HeroStats =
    HeroStats(
        strength = (strength + delta.strength).coerceAtLeast(0),
        vitality = (vitality + delta.vitality).coerceAtLeast(0),
        dexterity = (dexterity + delta.dexterity).coerceAtLeast(0),
        stamina = (stamina + delta.stamina).coerceAtLeast(0),
    )

