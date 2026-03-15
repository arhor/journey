package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.HeroEntity
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroEnergy
import com.github.arhor.journey.domain.model.HeroStats
import com.github.arhor.journey.domain.model.Progression
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.Test

class HeroMapperTest {

    @Test
    fun `toDomain should map and clamp hero energy when entity contains out of range energy values`() {
        // Given
        val createdAt = Instant.parse("2026-01-01T00:00:00Z")
        val updatedAt = Instant.parse("2026-01-02T00:00:00Z")
        val entity = HeroEntity(
            id = "hero-1",
            name = "Ari",
            level = 4,
            xpInLevel = 450L,
            strength = 10,
            vitality = 12,
            dexterity = 9,
            stamina = 8,
            energyNow = 999,
            energyMax = 0,
            createdAtMs = createdAt.toEpochMilli(),
            updatedAtMs = updatedAt.toEpochMilli(),
        )

        // When
        val actual = entity.toDomain()

        // Then
        actual.id shouldBe "hero-1"
        actual.name shouldBe "Ari"
        actual.progression shouldBe Progression(level = 4, xpInLevel = 450L)
        actual.stats shouldBe HeroStats(strength = 10, vitality = 12, dexterity = 9, stamina = 8)
        actual.energy shouldBe HeroEnergy(now = 1, max = 1)
        actual.createdAt shouldBe createdAt
        actual.updatedAt shouldBe updatedAt
    }

    @Test
    fun `toEntity should map domain hero fields when hero is provided`() {
        // Given
        val hero = Hero(
            id = "hero-9",
            name = "Nova",
            stats = HeroStats(strength = 6, vitality = 7, dexterity = 8, stamina = 9),
            progression = Progression(level = 2, xpInLevel = 120L),
            energy = HeroEnergy(now = 30, max = 50),
            createdAt = Instant.parse("2026-01-10T08:00:00Z"),
            updatedAt = Instant.parse("2026-01-10T09:00:00Z"),
        )

        // When
        val actual = hero.toEntity()

        // Then
        val expected = HeroEntity(
            id = "hero-9",
            name = "Nova",
            level = 2,
            xpInLevel = 120L,
            strength = 6,
            vitality = 7,
            dexterity = 8,
            stamina = 9,
            energyNow = 30,
            energyMax = 50,
            createdAtMs = Instant.parse("2026-01-10T08:00:00Z").toEpochMilli(),
            updatedAtMs = Instant.parse("2026-01-10T09:00:00Z").toEpochMilli(),
        )
        actual shouldBe expected
    }
}
