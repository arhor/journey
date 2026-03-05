package com.github.arhor.journey.domain.model

import java.time.Duration
import java.time.Instant

enum class ActivityType {
    WALK,
    RUN,
    WORKOUT,
    STRETCHING,
    REST,
}

enum class ActivitySource {
    MANUAL,
    IMPORTED,
}

/**
 * Raw activity data recorded by the app or imported from an external provider.
 *
 * This model is intentionally provider-agnostic and does not include platform-specific identifiers.
 */
data class RecordedActivity(
    val type: ActivityType,
    val source: ActivitySource,
    val startedAt: Instant,
    val duration: Duration,
    val distanceMeters: Int?,
    val steps: Int?,
    val note: String?,
)

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

