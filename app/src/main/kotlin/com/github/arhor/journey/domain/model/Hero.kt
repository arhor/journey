package com.github.arhor.journey.domain.model

import java.time.Instant
import kotlin.ConsistentCopyVisibility

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
    val energy: HeroEnergy = HeroEnergy(current = 100, max = 100),
    val createdAt: Instant,
    val updatedAt: Instant,
)

@ConsistentCopyVisibility
data class HeroEnergy private constructor(
    val current: Int,
    val max: Int,
) {
    companion object {
        operator fun invoke(current: Int, max: Int): HeroEnergy {
            val clampedMax = max.coerceAtLeast(1)
            val clampedCurrent = current.coerceIn(minimumValue = 0, maximumValue = clampedMax)
            return HeroEnergy(current = clampedCurrent, max = clampedMax)
        }
    }
}
