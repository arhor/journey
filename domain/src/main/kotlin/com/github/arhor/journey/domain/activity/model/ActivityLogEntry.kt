package com.github.arhor.journey.domain.activity.model

import com.github.arhor.journey.domain.model.Reward

/**
 * A single, persisted activity log entry.
 *
 * [reward] represents what was granted at logging time.
 */
data class ActivityLogEntry(
    val id: Long,
    val recorded: RecordedActivity,
    val reward: Reward,
)
