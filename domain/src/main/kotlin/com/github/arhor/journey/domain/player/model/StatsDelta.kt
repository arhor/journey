package com.github.arhor.journey.domain.player.model

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
