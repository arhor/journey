package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.ActivityLogEntity
import com.github.arhor.journey.domain.model.ActivitySource
import com.github.arhor.journey.domain.model.ActivityType
import com.github.arhor.journey.domain.model.ImportedActivityMetadata
import com.github.arhor.journey.domain.model.RecordedActivity
import com.github.arhor.journey.domain.model.Reward
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import org.junit.Test

class ActivityLogMapperTest {

    @Test
    fun `toDomain should use fallbacks when entity contains unknown enums and partial import metadata`() {
        // Given
        val startedAt = Instant.parse("2026-02-01T10:00:00Z")
        val entity = ActivityLogEntity(
            id = 42L,
            type = "UNSUPPORTED",
            source = "NOT_A_SOURCE",
            startedAtMs = startedAt.toEpochMilli(),
            durationSeconds = -12L,
            distanceMeters = 1500,
            steps = 2300,
            note = "Morning import",
            rewardXp = 80L,
            rewardEnergyDelta = 2,
            externalRecordId = "external-1",
            originPackageName = null,
            timeBoundsHash = "hash-1",
        )

        // When
        val actual = entity.toDomain()

        // Then
        actual.id shouldBe 42L
        actual.recorded.type shouldBe ActivityType.WALK
        actual.recorded.source shouldBe ActivitySource.MANUAL
        actual.recorded.startedAt shouldBe startedAt
        actual.recorded.duration shouldBe Duration.ZERO
        actual.recorded.importMetadata.shouldBeNull()
        actual.reward.xp shouldBe 80L
        actual.reward.energyDelta shouldBe 2
    }

    @Test
    fun `toEntity should clamp negative values and map import metadata when recorded activity is provided`() {
        // Given
        val recorded = RecordedActivity(
            type = ActivityType.RUN,
            source = ActivitySource.IMPORTED,
            startedAt = Instant.parse("2026-02-02T11:30:00Z"),
            duration = Duration.ofSeconds(-40L),
            distanceMeters = 5000,
            steps = 6000,
            note = "Run",
            importMetadata = ImportedActivityMetadata(
                externalRecordId = "external-2",
                originPackageName = "com.example.health",
                timeBoundsHash = "hash-2",
            ),
        )
        val reward = Reward(xp = -15L, energyDelta = 3)

        // When
        val actual = recorded.toEntity(reward = reward)

        // Then
        actual.type shouldBe ActivityType.RUN.name
        actual.source shouldBe ActivitySource.IMPORTED.name
        actual.durationSeconds shouldBe 0L
        actual.rewardXp shouldBe 0L
        actual.rewardEnergyDelta shouldBe 3
        actual.externalRecordId shouldBe "external-2"
        actual.originPackageName shouldBe "com.example.health"
        actual.timeBoundsHash shouldBe "hash-2"
    }
}
