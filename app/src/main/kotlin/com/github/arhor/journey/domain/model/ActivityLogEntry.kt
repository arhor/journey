package com.github.arhor.journey.domain.model

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

