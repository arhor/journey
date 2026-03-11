package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.HeroEntity
import com.github.arhor.journey.domain.player.model.Hero
import com.github.arhor.journey.domain.player.model.HeroEnergy
import com.github.arhor.journey.domain.player.model.HeroStats
import com.github.arhor.journey.domain.model.Progression
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant

class HeroMapperTest {

    @Test
    fun `toDomain and toEntity should preserve hero values for valid energy`() {
        // Given
        val hero = Hero(
            id = "hero-1",
            name = "Ayla",
            stats = HeroStats(strength = 2, vitality = 3, dexterity = 4, stamina = 5),
            progression = Progression(level = 7, xpInLevel = 42L),
            energy = HeroEnergy(current = 70, max = 100),
            createdAt = Instant.ofEpochMilli(1_000L),
            updatedAt = Instant.ofEpochMilli(2_000L),
        )

        // When
        val mapped = hero.toEntity().toDomain()

        // Then
        mapped shouldBe hero
    }

    @Test
    fun `toDomain should clamp energy values when entity has invalid values`() {
        // Given
        val entity = HeroEntity(
            id = "hero-2",
            name = "Bryn",
            level = 3,
            xpInLevel = 13L,
            strength = 1,
            vitality = 1,
            dexterity = 1,
            stamina = 1,
            energyCurrent = 999,
            energyMax = -10,
            createdAtMs = 5_000L,
            updatedAtMs = 9_000L,
        )

        // When
        val hero = entity.toDomain()

        // Then
        hero.energy shouldBe HeroEnergy(current = 1, max = 1)
    }
}
