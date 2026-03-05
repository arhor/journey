package com.github.arhor.journey.domain.model

/**
 * Persistent progression state.
 *
 * XP is stored as "XP in current level" so leveling rules can change without backfilling a global XP value.
 */
data class Progression(
    val level: Int,
    val xpInLevel: Long,
)

