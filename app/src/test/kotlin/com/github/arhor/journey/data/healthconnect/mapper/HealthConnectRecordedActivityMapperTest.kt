package com.github.arhor.journey.data.healthconnect.mapper

import com.github.arhor.journey.domain.model.ActivitySource
import com.github.arhor.journey.domain.model.ActivityType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
    fun `mapSession should generate identical import hash when sessions share the same time range`() {
        // Given
        val startedAt = Instant.parse("2025-01-10T10:00:00Z")
        val endedAt = Instant.parse("2025-01-10T10:45:00Z")
        val first = HealthConnectSessionInput(
            externalRecordId = "record-1",
            originPackageName = "com.example.first",
            exerciseType = HealthConnectExerciseType.WALKING,
            sourceType = HealthConnectSourceType.HEALTH_CONNECT,
            startedAt = startedAt,
            endedAt = endedAt,
            steps = 1_234,
            distanceMeters = 1_000.0,
            note = "first",
        )
        val second = first.copy(
            externalRecordId = "record-2",
            originPackageName = "com.example.second",
            exerciseType = HealthConnectExerciseType.RUNNING,
            steps = 4_321,
            note = "second",
        )

        // When
        val firstResult = mapper.mapSession(first)
        val secondResult = mapper.mapSession(second)

        // Then
        firstResult.importMetadata?.timeBoundsHash shouldBe secondResult.importMetadata?.timeBoundsHash
    }

    @Test
    fun `mapSession should generate different import hash when session time range changes`() {
        // Given
        val startedAt = Instant.parse("2025-01-10T10:00:00Z")
        val first = HealthConnectSessionInput(
            externalRecordId = "record-1",
            originPackageName = "com.example.provider",
            exerciseType = HealthConnectExerciseType.WALKING,
            sourceType = HealthConnectSourceType.HEALTH_CONNECT,
            startedAt = startedAt,
            endedAt = Instant.parse("2025-01-10T10:45:00Z"),
            steps = 100,
            distanceMeters = 120.0,
            note = null,
        )
        val second = first.copy(endedAt = Instant.parse("2025-01-10T10:50:00Z"))

        // When
        val firstResult = mapper.mapSession(first)
        val secondResult = mapper.mapSession(second)

        // Then
        firstResult.importMetadata?.timeBoundsHash shouldNotBe secondResult.importMetadata?.timeBoundsHash
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
