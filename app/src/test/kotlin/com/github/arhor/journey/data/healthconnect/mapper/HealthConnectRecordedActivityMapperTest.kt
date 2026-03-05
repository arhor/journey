package com.github.arhor.journey.data.healthconnect.mapper

import com.github.arhor.journey.domain.model.ActivitySource
import com.github.arhor.journey.domain.model.ActivityType
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Duration
import java.time.Instant

class HealthConnectRecordedActivityMapperTest {

    private val mapper = HealthConnectRecordedActivityMapper()

    @Test
    fun `mapSession should map running session when health connect values are provided`() {
        // Given
        val startedAt = Instant.parse("2025-01-10T10:00:00Z")
        val endedAt = Instant.parse("2025-01-10T10:45:00Z")
        val input = HealthConnectSessionInput(
            externalRecordId = "record-1",
            originPackageName = "com.google.android.apps.healthdata",
            exerciseType = HealthConnectExerciseType.RUNNING,
            sourceType = HealthConnectSourceType.HEALTH_CONNECT,
            startedAt = startedAt,
            endedAt = endedAt,
            steps = 5_432,
            distanceMeters = 6_100.4,
            note = "Morning run",
        )

        // When
        val result = mapper.mapSession(input)

        // Then
        result.type shouldBe ActivityType.RUN
        result.source shouldBe ActivitySource.IMPORTED
        result.startedAt shouldBe startedAt
        result.duration shouldBe Duration.ofMinutes(45)
        result.steps shouldBe 5_432
        result.distanceMeters shouldBe 6_100
        result.note shouldBe "Morning run"
        result.importMetadata?.externalRecordId shouldBe "record-1"
        result.importMetadata?.originPackageName shouldBe "com.google.android.apps.healthdata"
    }

    @Test
    fun `mapSession should map safe defaults when unsupported values are provided`() {
        // Given
        val startedAt = Instant.parse("2025-01-10T10:00:00Z")
        val endedAt = Instant.parse("2025-01-10T09:30:00Z")
        val input = HealthConnectSessionInput(
            externalRecordId = "record-2",
            originPackageName = "com.example.provider",
            exerciseType = HealthConnectExerciseType.OTHER,
            sourceType = HealthConnectSourceType.UNKNOWN,
            startedAt = startedAt,
            endedAt = endedAt,
            steps = -1,
            distanceMeters = -15.0,
            note = null,
        )

        // When
        val result = mapper.mapSession(input)

        // Then
        result.type shouldBe ActivityType.WORKOUT
        result.source shouldBe ActivitySource.IMPORTED
        result.duration shouldBe Duration.ZERO
        result.steps shouldBe null
        result.distanceMeters shouldBe null
        result.note shouldBe null
    }

    @Test
    fun `mapExerciseType should map workout when unsupported exercise type is provided`() {
        // Given
        val exerciseType = HealthConnectExerciseType.OTHER

        // When
        val result = mapper.mapExerciseType(exerciseType)

        // Then
        result shouldBe ActivityType.WORKOUT
    }

    @Test
    fun `mapSource should map imported when source type is unknown`() {
        // Given
        val sourceType = HealthConnectSourceType.UNKNOWN

        // When
        val result = mapper.mapSource(sourceType)

        // Then
        result shouldBe ActivitySource.IMPORTED
    }
}
