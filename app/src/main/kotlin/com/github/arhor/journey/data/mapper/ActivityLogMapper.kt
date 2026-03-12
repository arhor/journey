package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.ActivityLogEntity
import com.github.arhor.journey.domain.activity.model.ActivityLogEntry
import com.github.arhor.journey.domain.activity.model.ActivitySource
import com.github.arhor.journey.domain.activity.model.ActivityType
import com.github.arhor.journey.domain.activity.model.ImportedActivityMetadata
import com.github.arhor.journey.domain.activity.model.RecordedActivity
import com.github.arhor.journey.domain.player.model.Reward
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
            importMetadata = if (
                externalRecordId != null &&
                originPackageName != null &&
                timeBoundsHash != null
            ) {
                ImportedActivityMetadata(
                    externalRecordId = externalRecordId,
                    originPackageName = originPackageName,
                    timeBoundsHash = timeBoundsHash,
                )
            } else {
                null
            },
        ),
        reward = Reward(xp = rewardXp, energyDelta = rewardEnergyDelta),
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
        rewardEnergyDelta = reward.energyDelta,
        externalRecordId = importMetadata?.externalRecordId,
        originPackageName = importMetadata?.originPackageName,
        timeBoundsHash = importMetadata?.timeBoundsHash,
    )
