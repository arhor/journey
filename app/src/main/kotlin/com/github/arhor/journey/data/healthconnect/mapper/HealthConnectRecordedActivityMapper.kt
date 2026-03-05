package com.github.arhor.journey.data.healthconnect.mapper

import com.github.arhor.journey.domain.model.ActivitySource
import com.github.arhor.journey.domain.model.ActivityType
import com.github.arhor.journey.domain.model.ImportedActivityMetadata
import com.github.arhor.journey.domain.model.RecordedActivity
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt
import javax.inject.Inject

data class HealthConnectSessionInput(
    val externalRecordId: String,
    val originPackageName: String,
    val exerciseType: HealthConnectExerciseType,
    val sourceType: HealthConnectSourceType,
    val startedAt: Instant,
    val endedAt: Instant,
    val steps: Long?,
    val distanceMeters: Double?,
    val note: String?,
)

enum class HealthConnectExerciseType {
    WALKING,
    RUNNING,
    STRENGTH_TRAINING,
    STRETCHING,
    OTHER,
}

enum class HealthConnectSourceType {
    HEALTH_CONNECT,
    UNKNOWN,
}

class HealthConnectRecordedActivityMapper @Inject constructor() {

    fun mapSession(input: HealthConnectSessionInput): RecordedActivity = RecordedActivity(
        type = mapExerciseType(input.exerciseType),
        source = mapSource(input.sourceType),
        startedAt = input.startedAt,
        duration = Duration.between(input.startedAt, input.endedAt).coerceAtLeast(Duration.ZERO),
        distanceMeters = input.distanceMeters?.takeIf { it >= 0.0 }?.roundToInt(),
        steps = input.steps?.takeIf { it >= 0L }?.toInt(),
        note = input.note,
        importMetadata = ImportedActivityMetadata(
            externalRecordId = input.externalRecordId,
            originPackageName = input.originPackageName,
            timeBoundsHash = computeTimeBoundsHash(input.startedAt, input.endedAt),
        ),
    )

    fun mapExerciseType(exerciseType: HealthConnectExerciseType): ActivityType = when (exerciseType) {
        HealthConnectExerciseType.WALKING -> ActivityType.WALK
        HealthConnectExerciseType.RUNNING -> ActivityType.RUN
        HealthConnectExerciseType.STRETCHING -> ActivityType.STRETCHING
        HealthConnectExerciseType.STRENGTH_TRAINING -> ActivityType.WORKOUT
        HealthConnectExerciseType.OTHER -> ActivityType.WORKOUT
    }

    fun mapSource(sourceType: HealthConnectSourceType): ActivitySource = when (sourceType) {
        HealthConnectSourceType.HEALTH_CONNECT -> ActivitySource.IMPORTED
        HealthConnectSourceType.UNKNOWN -> ActivitySource.IMPORTED
    }

    private fun computeTimeBoundsHash(startedAt: Instant, endedAt: Instant): String {
        val payload = "${startedAt.toEpochMilli()}:${endedAt.toEpochMilli()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
