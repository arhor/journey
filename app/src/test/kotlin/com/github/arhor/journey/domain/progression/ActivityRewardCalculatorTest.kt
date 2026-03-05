package com.github.arhor.journey.domain.progression

import com.github.arhor.journey.domain.model.ActivitySource
import com.github.arhor.journey.domain.model.ActivityType
import com.github.arhor.journey.domain.model.RecordedActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ActivityRewardCalculatorTest {

    private val calculator = ActivityRewardCalculator()

    @Test
    fun `walk rounds up to full minutes`() {
        val recorded = RecordedActivity(
            type = ActivityType.WALK,
            source = ActivitySource.MANUAL,
            startedAt = Instant.parse("2026-01-01T00:00:00Z"),
            duration = Duration.ofSeconds(30),
            distanceMeters = null,
            steps = null,
            note = null,
        )

        val reward = calculator.calculate(recorded)

        assertThat(reward.xp).isEqualTo(10L)
    }

    @Test
    fun `rest gives zero xp`() {
        val recorded = RecordedActivity(
            type = ActivityType.REST,
            source = ActivitySource.MANUAL,
            startedAt = Instant.parse("2026-01-01T00:00:00Z"),
            duration = Duration.ofMinutes(15),
            distanceMeters = null,
            steps = null,
            note = null,
        )

        val reward = calculator.calculate(recorded)

        assertThat(reward.xp).isEqualTo(0L)
    }
}

