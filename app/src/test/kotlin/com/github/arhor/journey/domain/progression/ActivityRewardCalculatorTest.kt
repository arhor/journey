package com.github.arhor.journey.domain.progression

import com.github.arhor.journey.domain.model.ActivitySource
import com.github.arhor.journey.domain.model.ActivityType
import com.github.arhor.journey.domain.model.RecordedActivity
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ActivityRewardCalculatorTest {

    private val calculator = ActivityRewardCalculator()

    @Test
    fun `calculate should return rounded up xp when walk duration is a partial minute`() {
        // Given
        val recorded = RecordedActivity(
            type = ActivityType.WALK,
            source = ActivitySource.MANUAL,
            startedAt = Instant.parse("2026-01-01T00:00:00Z"),
            duration = Duration.ofSeconds(30),
            distanceMeters = null,
            steps = null,
            note = null,
        )

        // When
        val reward = calculator.calculate(recorded)

        // Then
        reward.xp shouldBe 10L
    }

    @Test
    fun `calculate should return zero xp when activity type is rest`() {
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
    }
}
