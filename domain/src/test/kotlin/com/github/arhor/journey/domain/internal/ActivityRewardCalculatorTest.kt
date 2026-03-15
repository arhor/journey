package com.github.arhor.journey.domain.internal

import com.github.arhor.journey.domain.model.ActivitySource
import com.github.arhor.journey.domain.model.ActivityType
import com.github.arhor.journey.domain.model.RecordedActivity
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import org.junit.Test

class ActivityRewardCalculatorTest {

    private val subject = ActivityRewardCalculator()

    @Test
    fun `calculate should round up xp and award energy when walk activity has partial minute duration`() {
        // Given
        val activity = recordedActivity(
            type = ActivityType.WALK,
            durationSeconds = 61,
            steps = 1_200,
        )

        // When
        val result = subject.calculate(activity)

        // Then
        result.xp shouldBe 20L
        result.energyDelta shouldBe 2
    }

    @Test
    fun `calculate should derive workout energy from distance when workout has no step count`() {
        // Given
        val activity = recordedActivity(
            type = ActivityType.WORKOUT,
            durationSeconds = 600,
            distanceMeters = 1_000,
            steps = null,
        )

        // When
        val result = subject.calculate(activity)

        // Then
        result.xp shouldBe 200L
        result.energyDelta shouldBe 1
    }

    @Test
    fun `calculate should return zero reward when walk activity has negative duration and steps`() {
        // Given
        val activity = recordedActivity(
            type = ActivityType.WALK,
            durationSeconds = -30,
            steps = -200,
        )

        // When
        val result = subject.calculate(activity)

        // Then
        result.xp shouldBe 0L
        result.energyDelta shouldBe 0
    }

    private fun recordedActivity(
        type: ActivityType,
        durationSeconds: Long,
        distanceMeters: Int? = null,
        steps: Int? = null,
    ): RecordedActivity =
        RecordedActivity(
            type = type,
            source = ActivitySource.MANUAL,
            startedAt = Instant.parse("2026-01-01T00:00:00Z"),
            duration = Duration.ofSeconds(durationSeconds),
            distanceMeters = distanceMeters,
            steps = steps,
            note = null,
        )
}
