package com.github.arhor.journey.domain.progression

import com.github.arhor.journey.domain.model.ActivityType
import com.github.arhor.journey.domain.model.RecordedActivity
import com.github.arhor.journey.domain.model.Reward
import javax.inject.Inject

/**
 * Converts a recorded activity into a deterministic [Reward].
 *
 * This is intentionally simple and local-only. Future versions can incorporate distance/steps,
 * user settings, or balancing parameters without changing persistence or UI layers.
 */
class ActivityRewardCalculator @Inject constructor() {

    private val xpPerMinute: Map<ActivityType, Long> = mapOf(
        ActivityType.WALK to 10L,
        ActivityType.RUN to 15L,
        ActivityType.WORKOUT to 20L,
        ActivityType.STRETCHING to 5L,
        ActivityType.REST to 0L,
    )

    private val stepsPerEnergy: Int = 500
    private val workoutIntensityPerEnergyPoint: Int = 180
    private val restEnergyDelta: Int = 0

    fun calculate(recorded: RecordedActivity): Reward {
        val seconds = recorded.duration.seconds.coerceAtLeast(0L)
        val minutesRoundedUp = if (seconds == 0L) 0L else (seconds + 59L) / 60L
        val rate = xpPerMinute[recorded.type] ?: 0L

        return Reward(
            xp = minutesRoundedUp * rate,
            energyDelta = calculateEnergyDelta(recorded = recorded, minutesRoundedUp = minutesRoundedUp),
        )
    }

    private fun calculateEnergyDelta(recorded: RecordedActivity, minutesRoundedUp: Long): Int = when (recorded.type) {
        ActivityType.WALK,
        ActivityType.RUN,
        -> {
            val steps = recorded.steps ?: 0
            (steps.coerceAtLeast(0) / stepsPerEnergy).coerceAtLeast(0)
        }

        ActivityType.WORKOUT -> {
            val intensity = when {
                recorded.steps != null && recorded.steps > 0 -> recorded.steps
                recorded.distanceMeters != null && recorded.distanceMeters > 0 -> recorded.distanceMeters / 4
                else -> minutesRoundedUp.toInt() * 10
            }
            (intensity.coerceAtLeast(0) / workoutIntensityPerEnergyPoint).coerceAtLeast(0)
        }

        ActivityType.REST -> restEnergyDelta
        ActivityType.STRETCHING -> 0
    }
}

