package com.github.arhor.journey.data.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.github.arhor.journey.data.healthconnect.mapper.HealthConnectExerciseType
import com.github.arhor.journey.data.healthconnect.mapper.HealthConnectRecordedActivityMapper
import com.github.arhor.journey.data.healthconnect.mapper.HealthConnectSessionInput
import com.github.arhor.journey.data.healthconnect.mapper.HealthConnectSourceType
import com.github.arhor.journey.domain.model.HealthDataTimeRange
import com.github.arhor.journey.domain.model.RecordedActivity
import javax.inject.Inject

class HealthConnectDataSource @Inject constructor(
    private val healthConnectClient: HealthConnectClient,
    private val mapper: HealthConnectRecordedActivityMapper,
) {

    suspend fun readSteps(range: HealthDataTimeRange): List<StepsRecord> =
        healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(range.startTime, range.endTime),
            ),
        ).records

    suspend fun readExerciseSessions(range: HealthDataTimeRange): List<RecordedActivity> =
        healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(range.startTime, range.endTime),
            ),
        ).records.map { record ->
            mapper.mapSession(
                HealthConnectSessionInput(
                    exerciseType = record.exerciseType.toHealthConnectExerciseType(),
                    sourceType = HealthConnectSourceType.HEALTH_CONNECT,
                    startedAt = record.startTime,
                    endedAt = record.endTime,
                    steps = null,
                    distanceMeters = null,
                    note = record.notes ?: record.title,
                ),
            )
        }

    suspend fun readCalories(range: HealthDataTimeRange): List<Any> = emptyList()

    suspend fun readHeartRate(range: HealthDataTimeRange): List<Any> = emptyList()
}

private fun Int.toHealthConnectExerciseType(): HealthConnectExerciseType = when (this) {
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> HealthConnectExerciseType.WALKING
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> HealthConnectExerciseType.RUNNING
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> HealthConnectExerciseType.STRENGTH_TRAINING
    ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> HealthConnectExerciseType.STRETCHING
    else -> HealthConnectExerciseType.OTHER
}
