package com.github.arhor.journey.domain.model

@ConsistentCopyVisibility
data class HeroEnergy private constructor(
    val now: Int,
    val max: Int,
) {
    companion object {
        operator fun invoke(now: Int, max: Int): HeroEnergy {
            val clampedMax = max.coerceAtLeast(1)
            val clampedNow = now.coerceIn(minimumValue = 0, maximumValue = clampedMax)

            return HeroEnergy(now = clampedNow, max = clampedMax)
        }
    }
}
