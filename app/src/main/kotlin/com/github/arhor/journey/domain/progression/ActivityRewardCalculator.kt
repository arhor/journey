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

    fun calculate(recorded: RecordedActivity): Reward {
        val rate = xpPerMinute[recorded.type] ?: 0L
        val seconds = recorded.duration.seconds.coerceAtLeast(0L)
        val minutesRoundedUp = if (seconds == 0L) 0L else (seconds + 59L) / 60L
        return Reward(xp = minutesRoundedUp * rate)
    }
}

