package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.HeroEntity
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroEnergy
import com.github.arhor.journey.domain.model.Progression
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant

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
            energyNow = 999,
            energyMax = 0,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

        // When
        val actual = entity.toDomain()

        // Then
        actual.id shouldBe "hero-1"
        actual.name shouldBe "Ari"
        actual.progression shouldBe Progression(level = 4, xpInLevel = 450L)
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
            energyNow = 30,
            energyMax = 50,
            createdAt = Instant.parse("2026-01-10T08:00:00Z"),
            updatedAt = Instant.parse("2026-01-10T09:00:00Z"),
        )
        actual shouldBe expected
    }
}
