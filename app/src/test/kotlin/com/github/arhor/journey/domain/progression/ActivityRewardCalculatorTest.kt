package com.github.arhor.journey.domain.progression

import com.github.arhor.journey.domain.activity.model.ActivitySource
import com.github.arhor.journey.domain.activity.model.ActivityType
import com.github.arhor.journey.domain.activity.model.RecordedActivity
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ActivityRewardCalculatorTest {

    private val calculator = ActivityRewardCalculator()

    @Test
    fun `calculate should return rounded up xp and step-based energy when walk has partial minute with steps`() {
        // Given
        val recorded = RecordedActivity(
            type = ActivityType.WALK,
            source = ActivitySource.MANUAL,
            startedAt = Instant.parse("2026-01-01T00:00:00Z"),
            duration = Duration.ofSeconds(30),
            distanceMeters = null,
            steps = 1200,
            note = null,
        )

        // When
        val reward = calculator.calculate(recorded)

        // Then
        reward.xp shouldBe 10L
        reward.energyDelta shouldBe 2
    }

    @Test
    fun `calculate should return duration-intensity energy grant when activity type is workout`() {
        // Given
        val recorded = RecordedActivity(
            type = ActivityType.WORKOUT,
            source = ActivitySource.MANUAL,
            startedAt = Instant.parse("2026-01-01T00:00:00Z"),
            duration = Duration.ofMinutes(30),
            distanceMeters = null,
            steps = null,
            note = null,
        )

        // When
        val reward = calculator.calculate(recorded)

        // Then
        reward.xp shouldBe 600L
        reward.energyDelta shouldBe 1
    }

    @Test
    fun `calculate should return zero xp and energy when activity type is rest`() {
        // Given
        val recorded = RecordedActivity(
            type = ActivityType.REST,
            source = ActivitySource.MANUAL,
            startedAt = Instant.parse("2026-01-01T00:00:00Z"),
            duration = Duration.ofMinutes(15),
            distanceMeters = null,
            steps = null,
            note = null,
        )

        // When
        val reward = calculator.calculate(recorded)

        // Then
        reward.xp shouldBe 0L
        reward.energyDelta shouldBe 0
    }
}
