package com.github.arhor.journey.domain.player.model

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
