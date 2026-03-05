package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.ActivityLogEntity
import com.github.arhor.journey.domain.model.ActivityLogEntry
import com.github.arhor.journey.domain.model.ActivitySource
import com.github.arhor.journey.domain.model.ActivityType
import com.github.arhor.journey.domain.model.RecordedActivity
import com.github.arhor.journey.domain.model.Reward
import java.time.Duration
import java.time.Instant

fun ActivityLogEntity.toDomain(): ActivityLogEntry {
    val typeEnum = runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.WALK)
    val sourceEnum = runCatching { ActivitySource.valueOf(source) }.getOrDefault(ActivitySource.MANUAL)

    return ActivityLogEntry(
        id = id,
        recorded = RecordedActivity(
            type = typeEnum,
            source = sourceEnum,
            startedAt = Instant.ofEpochMilli(startedAtMs),
            duration = Duration.ofSeconds(durationSeconds.coerceAtLeast(0L)),
            distanceMeters = distanceMeters,
            steps = steps,
            note = note,
        ),
        reward = Reward(xp = rewardXp),
    )
}

fun RecordedActivity.toEntity(reward: Reward): ActivityLogEntity =
    ActivityLogEntity(
        type = type.name,
        source = source.name,
        startedAtMs = startedAt.toEpochMilli(),
        durationSeconds = duration.seconds.coerceAtLeast(0L),
        distanceMeters = distanceMeters,
        steps = steps,
        note = note,
        rewardXp = reward.xp.coerceAtLeast(0L),
    )

