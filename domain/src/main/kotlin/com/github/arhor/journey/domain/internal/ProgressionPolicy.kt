package com.github.arhor.journey.domain.internal

import javax.inject.Inject

/**
 * Rules for leveling.
 *
 * Keep this small and easy to adjust. The current foundation uses a linear curve: 1000 * level.
 */
internal class ProgressionPolicy @Inject constructor() {

    fun xpToNextLevel(level: Int): Long =
        1000L * level.coerceAtLeast(1)
}
