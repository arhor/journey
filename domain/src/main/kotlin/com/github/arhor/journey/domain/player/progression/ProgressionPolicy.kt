package com.github.arhor.journey.domain.player.progression

import javax.inject.Inject

/**
 * Rules for leveling.
 *
 * Keep this small and easy to adjust. The current foundation uses a linear curve: 1000 * level.
 */
class ProgressionPolicy @Inject constructor() {

    fun xpToNextLevel(level: Int): Long =
        1000L * level.coerceAtLeast(1)
}

